// 前端数据层冒烟测试：node miniprogram/tests/smoke.js
// 桩掉 wx / getApp，用 mock 数据层跑一遍 下单→支付→验收→尾款→报修→接单

const BASE = require('path').join(__dirname, '..');

global.wx = {
  getStorageSync: () => 'mock-token',
  setStorageSync: () => {},
  showToast: (o) => console.log('   [toast]', o.title),
  showModal: () => {},
  requestPayment: () => {},
  request: () => { throw new Error('mock 模式下不应发真实请求'); }
};
global.getApp = () => ({ login: () => Promise.resolve({ token: 'x' }) });

const api = require(BASE + '/utils/api');
const fmt = require(BASE + '/utils/format');

let failed = 0;
function ok(cond, label, extra) {
  if (cond) console.log('  ✅', label);
  else { failed++; console.log('  ❌', label, extra === undefined ? '' : JSON.stringify(extra)); }
}

(async () => {
  console.log('— 金额格式化');
  ok(fmt.yuan(328000) === '3,280', 'yuan(328000) = 3,280', fmt.yuan(328000));
  ok(fmt.yuan(6800000) === '68,000', 'yuan(6800000) = 68,000', fmt.yuan(6800000));
  ok(fmt.yuan(98450) === '984.50', 'yuan(98450) = 984.50', fmt.yuan(98450));
  ok(fmt.yuan(0) === '0', 'yuan(0) = 0', fmt.yuan(0));

  console.log('— 首页');
  const home = await api.getHome();
  ok(home.hotProducts.length === 4, '热销机型 4 个', home.hotProducts.length);
  ok(home.quickEntries.length === 4, '快捷入口 4 个');

  console.log('— 商品');
  const list = await api.getProducts({ categoryId: 2 });
  ok(list.total === 1 && list.list[0].id === 101, '按分类筛选工业平缝', list.total);
  const p = await api.getProduct(101);
  ok(p.skus.length === 3, '详情含 3 个配置');
  ok(p.depositAmount === 98400, '定金 = 30%', p.depositAmount);

  console.log('— 师傅');
  const techs = await api.getTechnicians({ skuId: 1011 });
  ok(techs.length === 3, '可预约师傅 3 位');

  console.log('— 下单');
  const co = await api.createOrder({
    requestId: 'test-1', skuId: 1011, quantity: 1, addressId: 55801,
    installDate: '2025-11-28', installSlot: 'AM', technicianId: 88
  });
  ok(co.totalAmount === 328000 && co.depositAmount === 98400, '金额计算正确', co);
  ok(co.status === 'UNPAID', '初始状态待付款');

  const dup = await api.createOrder({
    requestId: 'test-1', skuId: 1011, quantity: 1, addressId: 55801,
    installDate: '2025-11-28', installSlot: 'AM', technicianId: 88
  });
  ok(dup.orderId === co.orderId, '同 requestId 幂等，不重复下单', [co.orderId, dup.orderId]);

  let o = await api.getOrder(co.orderId);
  ok(o.paidAmount === 0 && o.balanceAmount === 328000, '待付款时已付 0 / 待付全额', [o.paidAmount, o.balanceAmount]);
  ok(o.depositAmount === 98400, '详情含 depositAmount（用于「支付定金 ¥984」）', o.depositAmount);
  ok(o.actions.indexOf('PAY_DEPOSIT') >= 0, 'actions 含 PAY_DEPOSIT');

  console.log('— 支付定金');
  const pp = await api.prepay(co.orderId, 'DEPOSIT');
  ok(pp.amount === 98400, '预支付金额 = 定金', pp.amount);
  await api.mockPaySuccess(co.orderId, 'DEPOSIT');
  o = await api.getOrder(co.orderId);
  ok(o.status === 'DEPOSIT_PAID', '状态 → 待安装');
  ok(o.paidAmount === 98400 && o.balanceAmount === 229600, '已付/待付更新', [o.paidAmount, o.balanceAmount]);
  ok(o.timeline.filter(t => t.done).length === 2, '时间轴 2 步已完成');

  console.log('— 验收 + 尾款');
  await api.acceptOrder(co.orderId, true);
  o = await api.getOrder(co.orderId);
  ok(o.status === 'PENDING_BALANCE', '状态 → 待付尾款');
  await api.mockPaySuccess(co.orderId, 'BALANCE');
  o = await api.getOrder(co.orderId);
  ok(o.status === 'COMPLETED' && o.balanceAmount === 0, '状态 → 已完成，欠款清零');

  console.log('— 取消订单');
  const c2 = await api.createOrder({
    requestId: 'test-2', skuId: 1021, quantity: 1, addressId: 55801,
    installDate: '2025-11-28', installSlot: 'PM', technicianId: null
  });
  await api.cancelOrder(c2.orderId, '测试取消');
  const o2 = await api.getOrder(c2.orderId);
  ok(o2.status === 'CANCELLED', '状态 → 已取消');

  console.log('— 订单列表');
  const all = await api.getOrders({ status: 'ALL' });
  ok(all.total >= 4, '列表包含新订单', all.total);
  const unpaid = await api.getOrders({ status: 'UNPAID' });
  ok(unpaid.list.every(x => x.status === 'UNPAID'), '按状态过滤正确');

  console.log('— 设备与报修');
  const assets = await api.getAssets();
  ok(assets.length === 2 && assets[0].warrantyStatus === 'IN', '设备档案 2 台');
  const rp = await api.createRepair({
    assetId: 7781, faultType: 'THREAD_BREAK', faultDesc: '高速断线', images: [], expectDate: '2025-11-25', expectSlot: 'AM'
  });
  ok(rp.inWarranty === true, '保内自动判定');
  ok(/^WO\d{12}$/.test(rp.workNo), '工单号格式 WO+日期+序号', rp.workNo);

  const wo = await api.getRepair(rp.workOrderId);
  ok(wo.statusText === '待派单', '工单状态文案', wo.statusText);

  console.log('— 师傅端');
  const tw = await api.getTechWorkorders({ status: 'PENDING' });
  ok(tw.list.length >= 1 && tw.list[0].faultText, '待接单列表含故障类型');
  await api.acceptWorkorder(tw.list[0].id);
  const tw2 = await api.getTechWorkorders({ status: 'ACCEPTED' });
  ok(tw2.list.length >= 1, '接单后进入进行中');

  console.log('\n结果:', failed === 0 ? 'PASS' : 'FAIL ' + failed);
  process.exit(failed === 0 ? 0 : 1);
})().catch((e) => { console.error('异常', e); process.exit(1); });
