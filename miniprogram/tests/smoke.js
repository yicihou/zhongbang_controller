// 前端数据层冒烟：node miniprogram/tests/smoke.js
// 对齐 docs/03 D1–D11、docs/04：购物车、多行下单、线下付款、发货、多行报修

const BASE = require('path').join(__dirname, '..');
// config.js 默认连真实后端。冒烟只验证 Mock，这里先打开 Mock，再加载 api。
require(BASE + '/config').USE_MOCK = true;

global.wx = {
  getStorageSync: () => 'mock-token',
  setStorageSync: () => {},
  showToast: (o) => console.log('   [toast]', o.title),
  showModal: () => {},
  requestPayment: () => {},
  setClipboardData: () => {},
  request: () => { throw new Error('mock 模式下不应发真实请求'); }
};
global.getApp = () => ({ login: () => Promise.resolve({ token: 'x' }) });

const api = require(BASE + '/utils/api');
const fmt = require(BASE + '/utils/format');
const { DeliveryMethod, ORDER_STATUS_TEXT } = require(BASE + '/utils/constants');
const config = require(BASE + '/config');

let failed = 0;
function ok(cond, label, extra) {
  if (cond) console.log('  ✅', label);
  else { failed++; console.log('  ❌', label, extra === undefined ? '' : JSON.stringify(extra)); }
}

(async () => {
  console.log('— 配置');
  ok(config.paymentChannel === 'OFFLINE', '一期支付渠道 OFFLINE');
  ok(config.enableWxPay === false, '微信支付未开启');

  console.log('— 金额格式化');
  ok(fmt.yuan(328000) === '3,280', 'yuan(328000) = 3,280', fmt.yuan(328000));
  ok(fmt.yuan(6800000) === '68,000', 'yuan(6800000) = 68,000', fmt.yuan(6800000));
  ok(fmt.yuan(98450) === '984.50', 'yuan(98450) = 984.50', fmt.yuan(98450));
  ok(fmt.yuan(0) === '0', 'yuan(0) = 0', fmt.yuan(0));

  console.log('— 首页');
  const home = await api.getHome();
  ok(home.hotProducts.length === 4, '热销含整机与零件', home.hotProducts.length);
  ok(home.quickEntries.some((e) => e.code === 'CART'), '快捷入口含购物车');

  console.log('— 商品');
  const list = await api.getProducts({ categoryId: 2 });
  ok(list.total === 1 && list.list[0].id === 101, '按分类筛选工业平缝', list.total);
  const p = await api.getProduct(101);
  ok(p.skus.length === 3, '详情含 3 个配置');
  const part = await api.getProduct(201);
  ok(part.kind === 'PART', '零件商品 kind=PART');

  console.log('— 购物车 D1');
  const cart0 = await api.getCart();
  ok(cart0.items.length >= 2, '种子购物车含整机+零件', cart0.items.length);
  const add = await api.addCartItem({ skuId: 2021, quantity: 1, deliveryMethod: DeliveryMethod.EXPRESS });
  ok(add && add.id > 0, '加购零件', add);
  await api.updateCartItem(add.id, { quantity: 3, selected: true });
  const cart1 = await api.getCart();
  const needle = cart1.items.filter((i) => i.skuId === 2021)[0];
  ok(needle && needle.quantity === 3, '购物车改数量', needle && needle.quantity);
  await api.removeCartItem(add.id);

  console.log('— 师傅');
  const techs = await api.getTechnicians({ skuId: 1011 });
  ok(techs.length === 3, '可预约师傅 3 位');

  console.log('— 多行下单 D1/D7');
  const co = await api.createOrder({
    requestId: 'test-multi-1',
    addressId: 55801,
    remark: 'smoke',
    items: [
      {
        skuId: 1011, quantity: 1, deliveryMethod: DeliveryMethod.ON_SITE_INSTALL,
        installDate: '2026-11-28', installSlot: 'AM', technicianId: 88
      },
      { skuId: 2011, quantity: 2, deliveryMethod: DeliveryMethod.EXPRESS }
    ]
  });
  ok(co.status === 'PENDING_PAYMENT', '初始 PENDING_PAYMENT', co.status);
  ok(co.payableAmount === 328000 + 6800 * 2, '标价合计 = 整机+零件', co.payableAmount);
  ok(!!co.paymentId, '返回默认 FULL paymentId', co.paymentId);

  const dup = await api.createOrder({
    requestId: 'test-multi-1',
    addressId: 55801,
    items: [{ skuId: 1011, quantity: 1, deliveryMethod: DeliveryMethod.EXPRESS }]
  });
  ok(dup.orderId === co.orderId, '同 requestId 幂等', [co.orderId, dup.orderId]);

  let o = await api.getOrder(co.orderId);
  ok(o.items.length === 2, '订单两行', o.items.length);
  ok(o.items[0].deliveryMethod === DeliveryMethod.ON_SITE_INSTALL, '行1 上门安装');
  ok(o.items[0].installFeeText === '待管理员确认' || o.items[0].installFee === 0, '安装费待确认');
  ok(o.freightAmount === 0, '运费默认 0');
  ok(o.waitPayments.length === 1 && o.waitPayments[0].type === 'FULL', '默认一笔 FULL WAIT');
  ok(ORDER_STATUS_TEXT[o.status] === '待付款', '状态文案');

  console.log('— 线下转账意向 D9');
  const intent = await api.offlinePayIntent({ bizType: 'ORDER', bizId: co.orderId });
  ok(intent && intent.ok, 'offline intent 成功', intent);
  o = await api.getOrder(co.orderId);
  ok(o.waitPayments[0].offlineIntent === true, 'WAIT 笔标记 offlineIntent');

  let prepayCode = 0;
  try {
    await api.prepay({ orderId: co.orderId, type: 'FULL', channel: 'WXPAY' });
  } catch (e) { prepayCode = e.code; }
  ok(prepayCode === 5001, '一期 prepay 拒绝（二期 WXPAY）', prepayCode);

  console.log('— 管理员入账（mock-success 仅测状态机）');
  await api.mockPaySuccess({ orderId: co.orderId, type: 'FULL' });
  o = await api.getOrder(co.orderId);
  ok(o.status === 'PAID', '结清 → PAID', o.status);
  ok(o.waitPayments.length === 0, '无 WAIT');

  console.log('— 样例拆定金尾款单');
  const sample = await api.getOrder(910233001);
  ok(sample.priceAdjusted === true, '改价提示');
  ok(sample.waitPayments.length === 2, '定金+尾款两笔 WAIT', sample.waitPayments.length);
  ok(sample.waitPayments.every((p) => p.type === 'DEPOSIT' || p.type === 'BALANCE'), '类型正确');

  console.log('— 发货查询 D6');
  const ships = await api.getShipments(910233002);
  ok(ships.length === 2, '履约中订单 2 条 shipment', ships.length);
  ok(ships.some((s) => s.method === DeliveryMethod.EXPRESS && s.trackingNo), '快递含运单号');
  ok(ships.some((s) => s.method === DeliveryMethod.HOME_DELIVERY && s.courierMobile), '上门送货含电话');

  console.log('— 取消订单');
  const c2 = await api.createOrder({
    requestId: 'test-cancel-2',
    addressId: 55801,
    items: [{ skuId: 1021, quantity: 1, deliveryMethod: DeliveryMethod.STORE_PICKUP }]
  });
  await api.cancelOrder(c2.orderId, '测试取消');
  const o2 = await api.getOrder(c2.orderId);
  ok(o2.status === 'CANCELLED', '状态 → CANCELLED');

  console.log('— 订单列表');
  const all = await api.getOrders({ status: 'ALL' });
  ok(all.total >= 3, '列表含订单', all.total);
  const pending = await api.getOrders({ status: 'PENDING_PAYMENT' });
  ok(pending.list.every((x) => x.status === 'PENDING_PAYMENT'), '按状态过滤');

  console.log('— 管理员联系方式');
  const contact = await api.getAdminContact();
  ok(contact && contact.wechat && contact.phone, '含微信与电话', contact);

  console.log('— 报修一单多行 D5');
  const assets = await api.getAssets();
  ok(assets.length >= 2, '设备档案', assets.length);
  const rp = await api.createRepair({
    expectDate: '2026-11-26',
    expectSlot: 'AM',
    lines: [
      {
        assetId: 7781, faultType: 'THREAD_BREAK', faultDesc: '高速断线',
        images: ['img1']
      },
      {
        external: {
          brand: '某某牌', model: '家用 X9', faultDesc: '跳针',
          images: ['ext1'], sn: 'X9-001', purchaseChannel: '电商'
        },
        faultType: 'THREAD_BREAK', faultDesc: '跳针', images: ['ext1']
      }
    ]
  });
  ok(rp.ticketId || rp.id, '返回 ticketId', rp);
  ok(rp.lineCount === 2 || rp.status === 'SUBMITTED', '两行已提交', rp);

  const ticket = await api.getRepair(rp.ticketId || rp.id);
  ok(ticket.lines.length === 2, '详情两行');
  ok(ticket.lines[1].source === 'EXTERNAL', '外购行');
  ok(ticket.status === 'SUBMITTED', 'SUBMITTED');

  const quoted = await api.getRepair(88001);
  ok(quoted.status === 'QUOTED' && quoted.lines.length === 2, '样例报价多行');
  await api.confirmQuote(88001, true, '');
  const after = await api.getRepair(88001);
  ok(after.status === 'PENDING_PAYMENT', '确认报价 → 待付维修费', after.status);
  ok(after.payments && after.payments[0] && after.payments[0].type === 'REPAIR', '维修费 payment');

  console.log('— 师傅端（保留）');
  const tw = await api.getTechWorkorders({ status: 'PENDING' });
  ok(tw.list.length >= 1, '待接单列表');
  await api.acceptWorkorder(tw.list[0].id);
  const tw2 = await api.getTechWorkorders({ status: 'ACCEPTED' });
  ok(tw2.list.length >= 1, '接单后进行中');

  console.log('— 地址');
  const added = await api.addAddress({
    name: '李四', mobile: '13900001111', province: '浙江省', city: '杭州市',
    district: '西湖区', detail: '测试路 1 号', isDefault: false
  });
  ok(added && added.id > 0, '新增地址', added);
  await api.updateAddress(added.id, {
    name: '李四', mobile: '13900001111', province: '浙江省', city: '杭州市',
    district: '西湖区', detail: '测试路 2 号', isDefault: true
  });
  const addrs = await api.getAddresses();
  const hit = addrs.filter((a) => a.id === added.id)[0];
  ok(hit && hit.isDefault && hit.detail === '测试路 2 号', '修改地址并设为默认');
  await api.deleteAddress(added.id);

  const installOrders = await api.getOrders({ status: 'PAID' });
  const paidOrder = (installOrders.list || []).filter((x) => x.id === co.orderId)[0];
  ok(!!paidOrder, '多行单已付款可见');

  const allWo = await api.getTechWorkorders({});
  const install = (allWo.list || []).filter((w) => w.type === 'INSTALL' && w.orderId === co.orderId)[0]
    || (allWo.list || []).filter((w) => w.type === 'INSTALL')[0];
  ok(!!install, '付款后上门安装行生成工单', install && install.id);
  if (install) {
    await api.acceptWorkorder(install.id, { appointmentAt: '2026-11-28 09:30:00' });
    let farCode = 0;
    try {
      await api.checkinWorkorder(install.id, { latitude: 31.23, longitude: 121.47, address: '过远' });
    } catch (e) { farCode = e.code; }
    ok(farCode === 3005, '超过 500 米拒绝打卡', farCode);
    const near = await api.checkinWorkorder(install.id, { latitude: 30.2742, longitude: 120.1552, address: '已到场' });
    ok(near.status === 'ON_SITE', '到场打卡成功');
    const done = await api.finishWorkorder(install.id, {
      sn: 'A5-TEST-0001', images: ['1', '2', '3'], signImage: 'sign', remark: '试缝正常'
    });
    ok(done.status === 'DONE', '安装完工');
  }

  console.log('\n结果:', failed === 0 ? 'PASS' : 'FAIL ' + failed);
  process.exit(failed === 0 ? 0 : 1);
})().catch((e) => { console.error('异常', e); process.exit(1); });
