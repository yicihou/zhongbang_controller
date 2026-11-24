/**
 * 本地 Mock 数据层：无需后端即可在开发者工具里跑通全部页面。
 * config.js 里 USE_MOCK = false 时自动改用真实 http 请求。
 */
const delay = (ms) => new Promise((r) => setTimeout(r, ms || 120));
const now = () => {
  const d = new Date();
  const p = (n) => (n < 10 ? '0' + n : '' + n);
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' +
    p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
};

/* ----------------------------- 静态数据 ----------------------------- */
const PRODUCTS = {
  101: {
    id: 101, name: '电脑平缝机 A5', model: 'A5-5000', coverImage: '', price: 328000,
    depositRate: 30, stock: 12, tags: ['含上门安装', '现货'], categoryId: 2,
    subtitle: '直驱 · 自动剪线 · 5000 针/分', brand: '标准牌',
    warranty: { months: 12, motorMonths: 24, desc: '整机 12 个月，电机电控 24 个月，以安装完成日起算' },
    services: ['含上门安装调试', '含首次操作培训', '可开增值税专用发票'],
    specs: [
      { name: '最高转速', value: '5000 针/分' },
      { name: '适用场景', value: '服装厂流水线 / 加工坊' },
      { name: '电压', value: '220V' },
      { name: '整机重量', value: '32 kg' }
    ],
    skus: [
      { id: 1011, name: '标准台板 + 伺服电机', specs: { 台板: '标准', 电机: '伺服' }, price: 328000, stock: 8 },
      { id: 1012, name: '加厚台板 + 伺服电机', specs: { 台板: '加厚', 电机: '伺服' }, price: 336000, stock: 4 },
      { id: 1013, name: '标准台板 + 直驱电机', specs: { 台板: '标准', 电机: '直驱' }, price: 354000, stock: 0 }
    ]
  },
  102: {
    id: 102, name: '家用多功能机 M3', model: 'M3', coverImage: '', price: 158000,
    depositRate: 30, stock: 20, tags: ['含上门教学'], categoryId: 1,
    subtitle: '锁边 · 绣花 · 家用', brand: '标准牌',
    warranty: { months: 12, motorMonths: 24, desc: '整机 12 个月' },
    services: ['含上门教学', '可开增值税专用发票'],
    specs: [{ name: '针数', value: '多针' }, { name: '适用场景', value: '家庭 / 裁缝店' }],
    skus: [{ id: 1021, name: '标准套装', specs: { 套装: '标准' }, price: 158000, stock: 20 }]
  },
  103: {
    id: 103, name: '四线包缝机 E4', model: 'E4-7000', coverImage: '', price: 690000,
    depositRate: 30, stock: 3, tags: ['现货 3 台'], categoryId: 3,
    subtitle: '超高速 · 低噪', brand: '标准牌',
    warranty: { months: 12, motorMonths: 24, desc: '整机 12 个月' },
    services: ['含上门安装调试'],
    specs: [{ name: '最高转速', value: '7000 针/分' }, { name: '适用场景', value: '针织 / 内衣' }],
    skus: [{ id: 1031, name: '标准配置', specs: { 配置: '标准' }, price: 690000, stock: 3 }]
  },
  104: {
    id: 104, name: '电脑绣花机 12 头', model: 'EM-12', coverImage: '', price: 6800000,
    depositRate: 30, stock: 2, tags: ['可分期'], categoryId: 4,
    subtitle: '适用帽子 / 成衣', brand: '标准牌',
    warranty: { months: 12, motorMonths: 24, desc: '整机 12 个月' },
    services: ['含上门安装调试', '含操作培训'],
    specs: [{ name: '头数', value: '12 头' }, { name: '适用场景', value: '成衣 / 帽子绣花' }],
    skus: [{ id: 1041, name: '12 头标准型', specs: { 头数: '12' }, price: 6800000, stock: 2 }]
  }
};

const TECHS = [
  {
    id: 88, name: '王师傅', avatar: '', rating: 4.9, orderCount: 186, distanceKm: 2.4,
    skills: ['平缝', '包缝', '电控调试'], intro: '8 年安装经验，擅长流水线调试', recommended: true,
    freeSlots: [
      { date: '2025-11-28', slot: 'AM', text: '11-28 上午' },
      { date: '2025-11-28', slot: 'PM', text: '11-28 下午' }
    ]
  },
  {
    id: 89, name: '李师傅', avatar: '', rating: 4.7, orderCount: 243, distanceKm: 5.1,
    skills: ['绣花机', '特种机'], intro: '擅长绣花机与特种机', recommended: false,
    freeSlots: [
      { date: '2025-11-27', slot: 'AM', text: '11-27 上午' },
      { date: '2025-11-28', slot: 'PM', text: '11-28 下午' }
    ]
  },
  {
    id: 90, name: '赵师傅', avatar: '', rating: 4.6, orderCount: 97, distanceKm: 8.7,
    skills: ['家用机', '安装教学'], intro: '擅长家用机安装与操作培训', recommended: false,
    freeSlots: [
      { date: '2025-11-27', slot: 'AM', text: '11-27 上午' },
      { date: '2025-11-28', slot: 'AM', text: '11-28 上午' }
    ]
  }
];

const ADDRESSES = [
  { id: 55801, name: '张建国', mobile: '138****8000', province: '浙江省', city: '杭州市', district: '余杭区', detail: '某某街道 128 号 3 楼', isDefault: true },
  { id: 55802, name: '张建国', mobile: '138****8000', province: '浙江省', city: '杭州市', district: '萧山区', detail: '某某工业园 B 栋', isDefault: false }
];

const ASSETS = [
  { id: 7781, model: '电脑平缝机 A5', sn: 'A5-2024-008671', buyDate: '2024-05-20', warrantyEnd: '2026-05-20', warrantyStatus: 'IN', warrantyText: '保内，至 2026-05-20', lastServiceAt: '2025-11-23' },
  { id: 7782, model: '四线包缝机 E4', sn: 'E4-2023-002114', buyDate: '2023-03-11', warrantyEnd: '2024-03-11', warrantyStatus: 'OUT', warrantyText: '已过保', lastServiceAt: '' }
];

const STATUS_TEXT = {
  UNPAID: '待付款', DEPOSIT_PAID: '待安装', INSTALLING: '安装中',
  PENDING_BALANCE: '待付尾款', COMPLETED: '已完成', CANCELLED: '已取消',
  PENDING: '待派单', ACCEPTED: '已接单', ON_SITE: '服务中', DONE: '已完成', CLOSED: '已关闭'
};

/* ----------------------------- 内存状态 ----------------------------- */
let seq = { order: 2, work: 12 };
const ORDERS = {};
const WORKORDERS = {};

function seed() {
  const mk = (i, status, tech, prod, sku) => {
    const deposit = Math.floor(sku.price * 0.3);
    const id = 910233001 + i;
    ORDERS[id] = {
      id,
      orderNo: 'SO2025112300012' + i,
      status,
      totalAmount: sku.price,
      depositAmount: deposit,
      paidAmount: status === 'UNPAID' ? 0 : deposit,
      balanceAmount: status === 'UNPAID' ? sku.price : sku.price - deposit,
      createdAt: '2025-11-23 20:14:0' + i,
      installDate: '2025-11-2' + (8 - i),
      installSlot: 'AM',
      remark: '厂房在 3 楼，有货梯',
      technician: { id: tech.id, name: tech.name, mobile: '138****8000', rating: tech.rating },
      technicianName: tech.name,
      address: ADDRESSES[0],
      invoice: { need: false },
      items: [{ skuId: sku.id, name: prod.name, skuName: sku.name, coverImage: '', price: sku.price, quantity: 1 }],
      times: { UNPAID: '2025-11-23 20:14:0' + i, DEPOSIT_PAID: '2025-11-23 20:16:22' },
      payments: status === 'UNPAID' ? [] : [{ id: 501, type: 'DEPOSIT', typeText: '定金', amount: deposit, status: 'SUCCESS', paidAt: '2025-11-23 20:16:22' }],
      expireAt: ''
    };
  };
  mk(0, 'PENDING_BALANCE', TECHS[0], PRODUCTS[101], PRODUCTS[101].skus[0]);
  mk(1, 'INSTALLING', TECHS[1], PRODUCTS[103], PRODUCTS[103].skus[0]);

  WORKORDERS[660012] = {
    id: 660012, workNo: 'WO20251123000045', type: 'REPAIR', status: 'ACCEPTED',
    faultType: 'THREAD_BREAK', faultDesc: '高速运转时断线，低速正常，已换过机针',
    inWarranty: true, warrantyDesc: '整机保修至 2026-05-20，本次故障在保修范围内',
    quoteAmount: 0, actualAmount: 0, appointmentAt: '2025-11-25 09:30:00',
    createdAt: '2025-11-23 20:14:00', customerName: '张建国',
    address: '余杭区某某街道 128 号 3 楼', distanceKm: 2.4,
    technician: { id: 89, name: '李师傅', mobile: '138****8000', rating: 4.7 },
    images: [],
    timeline: [
      { status: 'PENDING', title: '提交报修', desc: '已判定保内，等待派单', time: '2025-11-23 20:14:00', done: true, current: false },
      { status: 'ACCEPTED', title: '师傅已接单', desc: '约定 11-25 上午上门', time: '2025-11-24 09:02:00', done: true, current: true },
      { status: 'DONE', title: '维修完成', desc: '', time: '', done: false, current: false }
    ]
  };
}
seed();

/* ----------------------------- 工具 ----------------------------- */
const ok = (data) => ({ statusCode: 200, body: { code: 0, msg: 'ok', data, traceId: 'mock' } });
const fail = (code, msg) => ({ statusCode: 200, body: { code, msg, data: null, traceId: 'mock' } });

function findSku(skuId) {
  const ids = Object.keys(PRODUCTS);
  for (let i = 0; i < ids.length; i++) {
    const p = PRODUCTS[ids[i]];
    for (let j = 0; j < p.skus.length; j++) {
      if (p.skus[j].id === Number(skuId)) return { product: p, sku: p.skus[j] };
    }
  }
  return {};
}

function brief(o) {
  return {
    id: o.id, orderNo: o.orderNo, status: o.status,
    statusText: STATUS_TEXT[o.status] || o.status,
    totalAmount: o.totalAmount, depositAmount: o.depositAmount,
    paidAmount: o.paidAmount, balanceAmount: o.balanceAmount,
    createdAt: o.createdAt, installDate: o.installDate,
    technicianName: o.technicianName, items: o.items
  };
}

function timelineOf(o) {
  const seqArr = ['UNPAID', 'DEPOSIT_PAID', 'INSTALLING', 'PENDING_BALANCE', 'COMPLETED'];
  const texts = {
    UNPAID: ['提交订单', '等待支付定金'],
    DEPOSIT_PAID: ['已付定金', '已派单给师傅'],
    INSTALLING: ['安装中', '师傅已上门施工'],
    PENDING_BALANCE: ['安装完成待付尾款', '验收通过，尾款待支付'],
    COMPLETED: ['订单完成', '已结清并归档']
  };
  const idx = seqArr.indexOf(o.status);
  return seqArr.map((s, i) => ({
    status: s, title: texts[s][0], desc: texts[s][1],
    time: (o.times && o.times[s]) || '',
    done: i <= idx, current: i === idx
  }));
}

function actionsOf(o) {
  if (o.status === 'UNPAID') return ['PAY_DEPOSIT', 'CANCEL'];
  if (o.status === 'PENDING_BALANCE') return ['PAY_BALANCE', 'CALL_TECHNICIAN', 'REPAIR'];
  if (o.status === 'DEPOSIT_PAID') return ['CALL_TECHNICIAN'];
  if (o.status === 'COMPLETED') return ['EVALUATE', 'REPAIR'];
  return [];
}

/* ----------------------------- 路由 ----------------------------- */
const ROUTES = [
  ['POST', /^\/api\/auth\/login$/, () => ok({
    token: 'mock-token', expiresIn: 2592000, hasMobile: false,
    customer: { id: 1001, nickname: '', avatar: '', mobile: '', level: 1, levelText: '普通客户' }
  })],

  ['GET', /^\/api\/home$/, () => ok({
    banners: [{ id: 1, image: '', title: '新机到家，师傅上门', subtitle: '下单可选安装师傅 · 验收后再付尾款', linkType: 'PRODUCT_LIST', linkValue: '0' }],
    quickEntries: [
      { code: 'BUY', title: '买机器', icon: '机', badge: '' },
      { code: 'REPAIR', title: '一键报修', icon: '修', badge: '' },
      { code: 'ASSET', title: '我的设备', icon: '档', badge: '2' },
      { code: 'INVOICE', title: '发票/对账', icon: '票', badge: '' }
    ],
    categories: [
      { id: 1, name: '家用多功能', count: 12 }, { id: 2, name: '工业平缝', count: 9 },
      { id: 3, name: '包缝绷缝', count: 6 }, { id: 4, name: '特种机', count: 5 }
    ],
    hotProducts: [101, 102, 103, 104].map((id) => {
      const p = PRODUCTS[id];
      return {
        id: p.id, name: p.name, model: p.model, coverImage: '', price: p.price,
        depositAmount: Math.floor(p.price * p.depositRate / 100), stock: p.stock, tags: p.tags
      };
    })
  })],

  ['GET', /^\/api\/categories$/, () => ok([
    { id: 1, name: '家用多功能', count: 12 }, { id: 2, name: '工业平缝', count: 9 },
    { id: 3, name: '包缝绷缝', count: 6 }, { id: 4, name: '特种机', count: 5 }
  ])],

  ['GET', /^\/api\/products$/, (m, path, q) => {
    let list = Object.keys(PRODUCTS).map((k) => PRODUCTS[k]);
    if (q && q.categoryId) list = list.filter((p) => String(p.categoryId) === String(q.categoryId));
    if (q && q.keyword) list = list.filter((p) => p.name.indexOf(q.keyword) >= 0 || p.model.indexOf(q.keyword) >= 0);
    list = list.map((p) => ({
      id: p.id, name: p.name, model: p.model, coverImage: '', price: p.price,
      depositAmount: Math.floor(p.price * p.depositRate / 100), stock: p.stock, tags: p.tags
    }));
    return ok({ total: list.length, page: 1, size: 20, list });
  }],

  ['GET', /^\/api\/products\/(\d+)$/, (m, path, q, id) => {
    const p = PRODUCTS[id];
    if (!p) return fail(1001, '机型不存在');
    const data = Object.assign({}, p);
    data.depositAmount = Math.floor(p.price * p.depositRate / 100);
    data.images = [];
    data.videoUrl = '';
    data.skus = p.skus.map((s) => Object.assign({}, s, {
      depositAmount: Math.floor(s.price * p.depositRate / 100)
    }));
    return ok(data);
  }],

  ['GET', /^\/api\/technicians\/available$/, () => ok(TECHS)],

  ['GET', /^\/api\/orders$/, (m, path, q) => {
    let list = Object.keys(ORDERS).map((k) => ORDERS[k]);
    if (q && q.status && q.status !== 'ALL') {
      if (q.status === 'AFTER_SALE') {
        list = [];
      } else {
        list = list.filter((o) => o.status === q.status);
      }
    }
    list.sort((a, b) => b.id - a.id);
    return ok({ total: list.length, page: 1, size: 20, list: list.map(brief) });
  }],

  ['POST', /^\/api\/orders$/, (m, path, body) => {
    if (body.requestId) {
      const hit = Object.keys(ORDERS).map((k) => ORDERS[k]).filter((o) => o.requestId === body.requestId)[0];
      if (hit) {
        return ok({
          orderId: hit.id, orderNo: hit.orderNo, totalAmount: hit.totalAmount,
          depositAmount: hit.depositAmount, balanceAmount: hit.balanceAmount,
          status: hit.status, expireAt: hit.expireAt
        });
      }
    }
    const found = findSku(body.skuId);
    if (!found.sku) return fail(1001, 'SKU 不存在');
    const qty = Number(body.quantity || 1);
    const total = found.sku.price * qty;
    const deposit = Math.floor(total * 0.3);
    let tech = null;
    if (body.technicianId) {
      tech = TECHS.filter((t) => t.id === Number(body.technicianId))[0];
      if (!tech) return fail(3002, '师傅档期已满，请重新选择');
    }
    seq.order += 1;
    const id = 910233000 + seq.order;
    const addr = ADDRESSES.filter((a) => a.id === Number(body.addressId))[0] || ADDRESSES[0];
    const o = {
      id, requestId: body.requestId,
      orderNo: 'SO20251123' + String(seq.order).padStart(4, '0'),
      status: 'UNPAID', totalAmount: total, depositAmount: deposit,
      paidAmount: 0, balanceAmount: total,
      createdAt: now(), installDate: body.installDate, installSlot: body.installSlot || 'AM',
      remark: body.remark || '',
      technician: tech ? { id: tech.id, name: tech.name, mobile: '138****8000', rating: tech.rating } : null,
      technicianName: tech ? tech.name : '',
      address: addr, invoice: body.invoice || { need: false },
      items: [{
        skuId: found.sku.id, name: found.product.name, skuName: found.sku.name,
        coverImage: '', price: found.sku.price, quantity: qty
      }],
      times: { UNPAID: now() }, payments: [],
      expireAt: now()
    };
    ORDERS[id] = o;
    return ok({
      orderId: id, orderNo: o.orderNo, totalAmount: total, depositAmount: deposit,
      balanceAmount: total - deposit, status: 'UNPAID', expireAt: o.expireAt
    });
  }],

  ['GET', /^\/api\/orders\/(\d+)$/, (m, path, q, id) => {
    const o = ORDERS[id];
    if (!o) return fail(1001, '订单不存在');
    const d = brief(o);
    d.installSlot = o.installSlot;
    d.remark = o.remark;
    d.expireAt = o.expireAt;
    d.technician = o.technician;
    d.address = o.address;
    d.invoice = o.invoice;
    d.payments = o.payments;
    d.timeline = timelineOf(o);
    d.actions = actionsOf(o);
    return ok(d);
  }],

  ['POST', /^\/api\/orders\/(\d+)\/cancel$/, (m, path, body, id) => {
    const o = ORDERS[id];
    if (!o) return fail(1001, '订单不存在');
    if (o.status !== 'UNPAID') return fail(3003, '当前状态不允许取消');
    o.status = 'CANCELLED';
    o.times.CANCELLED = now();
    return ok(true);
  }],

  ['POST', /^\/api\/orders\/(\d+)\/accept$/, (m, path, body, id) => {
    const o = ORDERS[id];
    if (!o) return fail(1001, '订单不存在');
    if (body.pass === false) return ok({ status: 'INSTALLING', balanceAmount: o.balanceAmount });
    o.status = 'PENDING_BALANCE';
    o.times.PENDING_BALANCE = now();
    return ok({ status: o.status, balanceAmount: o.balanceAmount });
  }],

  ['POST', /^\/api\/pay\/prepay$/, (m, path, body) => {
    const o = ORDERS[body.orderId];
    if (!o) return fail(1001, '订单不存在');
    const isDeposit = body.type === 'DEPOSIT';
    return ok({
      paymentId: 501,
      outTradeNo: o.orderNo + (isDeposit ? '-D' : '-B'),
      amount: isDeposit ? o.depositAmount : o.balanceAmount,
      payParams: { timeStamp: '' + Date.now(), nonceStr: 'mock', package: 'prepay_id=mock', signType: 'RSA', paySign: 'mock' }
    });
  }],

  // Mock 专用：模拟微信支付成功回调
  ['POST', /^\/api\/pay\/mock-success$/, (m, path, body) => {
    const o = ORDERS[body.orderId];
    if (!o) return fail(1001, '订单不存在');
    if (body.type === 'DEPOSIT') {
      o.status = 'DEPOSIT_PAID';
      o.paidAmount = o.depositAmount;
      o.balanceAmount = o.totalAmount - o.paidAmount;
      o.times.DEPOSIT_PAID = now();
      o.payments.push({ id: 501, type: 'DEPOSIT', typeText: '定金', amount: o.depositAmount, status: 'SUCCESS', paidAt: now() });
    } else {
      const amt = o.balanceAmount;
      o.paidAmount = o.totalAmount;
      o.balanceAmount = 0;
      o.status = 'COMPLETED';
      o.times.COMPLETED = now();
      o.payments.push({ id: 502, type: 'BALANCE', typeText: '尾款', amount: amt, status: 'SUCCESS', paidAt: now() });
    }
    return ok({ status: o.status });
  }],

  ['GET', /^\/api\/customer\/profile$/, () => ok({
    customer: { id: 1001, nickname: '张建国', avatar: '', mobile: '138****8000', level: 3, levelText: '金卡客户' },
    orderCount: Object.keys(ORDERS).length, totalPaid: 4260000,
    pendingAmount: 229600, assetCount: ASSETS.length, couponCount: 2
  })],

  ['GET', /^\/api\/customer\/addresses$/, () => ok(ADDRESSES)],

  ['GET', /^\/api\/assets$/, () => ok(ASSETS)],

  ['POST', /^\/api\/assets\/bind$/, (m, path, body) => {
    const hit = ASSETS.filter((a) => a.sn === body.sn)[0];
    return hit ? ok(hit) : fail(1001, '未查询到该机身码，请联系经销商');
  }],

  ['POST', /^\/api\/repairs$/, (m, path, body) => {
    seq.work += 1;
    const id = 660000 + seq.work;
    const asset = ASSETS.filter((a) => a.id === Number(body.assetId))[0] || ASSETS[0];
    const inW = asset.warrantyStatus === 'IN';
    WORKORDERS[id] = {
      id, workNo: 'WO20251123' + String(seq.work).padStart(4, '0'), type: 'REPAIR',
      status: 'PENDING', faultType: body.faultType, faultDesc: body.faultDesc,
      inWarranty: inW, warrantyDesc: inW ? ('整机保修至 ' + asset.warrantyEnd + '，本次故障在保修范围内') : '已过保，需先检测报价',
      quoteAmount: 0, actualAmount: 0, appointmentAt: '', createdAt: now(),
      customerName: '张建国', address: '余杭区某某街道 128 号 3 楼', distanceKm: 2.4,
      technician: null, images: body.images || [],
      timeline: [
        { status: 'PENDING', title: '提交报修', desc: '等待派单', time: now(), done: true, current: true },
        { status: 'ACCEPTED', title: '师傅接单', desc: '', time: '', done: false, current: false },
        { status: 'DONE', title: '维修完成', desc: '', time: '', done: false, current: false }
      ]
    };
    return ok({
      workOrderId: id, workNo: WORKORDERS[id].workNo, inWarranty: inW,
      warrantyDesc: WORKORDERS[id].warrantyDesc, status: 'PENDING', statusText: '待派单'
    });
  }],

  ['GET', /^\/api\/repairs\/(\d+)$/, (m, path, q, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    return ok(Object.assign({}, w, { typeText: '维修', statusText: STATUS_TEXT[w.status] || w.status }));
  }],

  ['GET', /^\/api\/tech\/workorders$/, (m, path, q) => {
    let list = Object.keys(WORKORDERS).map((k) => WORKORDERS[k]);
    if (q && q.status) list = list.filter((w) => w.status === q.status);
    const map = {
      THREAD_BREAK: '断线 / 跳针', NOISE: '噪音异响', OIL: '不上油',
      ELECTRIC: '电控故障', FEED: '送布异常', OTHER: '其他'
    };
    return ok({
      total: list.length, page: 1, size: 20,
      list: list.map((w) => ({
        id: w.id, workNo: w.workNo, type: w.type, typeText: '维修',
        status: w.status,
        statusText: w.status === 'PENDING' ? '待接单' : (STATUS_TEXT[w.status] || w.status),
        faultText: map[w.faultType] || '其他', inWarranty: w.inWarranty,
        appointmentAt: w.appointmentAt, customerName: w.customerName,
        address: w.address, distanceKm: w.distanceKm
      }))
    });
  }],

  ['POST', /^\/api\/tech\/workorders\/(\d+)\/accept$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    w.status = 'ACCEPTED';
    return ok({ status: 'ACCEPTED' });
  }],

  ['POST', /^\/api\/evaluations$/, () => ok(true)],

  ['GET', /^\/api\/upload\/policy$/, () => ok({
    host: '', dir: 'mock/', expireAt: 0, policy: 'mock', signature: 'mock', accessKeyId: 'mock'
  })]
];

/** 供 request.js 调用，签名与 wx.request 的 result 对齐 */
async function request(path, method, data) {
  await delay(100 + Math.random() * 120);
  const m = method || 'GET';
  for (let i = 0; i < ROUTES.length; i++) {
    const [rm, re, fn] = ROUTES[i];
    if (rm !== m) continue;
    const hit = re.exec(path);
    if (hit) return fn(m, path, data || {}, hit[1]);
  }
  return fail(404, 'Mock 未实现该接口: ' + m + ' ' + path);
}

module.exports = { request };
