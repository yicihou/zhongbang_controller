/**
 * Mock 数据层（docs/03 D1–D11、docs/04）。
 * USE_MOCK=true 时 request.js 走这里；金额单位为分。
 */
const {
  DeliveryMethod, ORDER_STATUS_TEXT, REPAIR_STATUS_TEXT,
  PAYMENT_TYPE_TEXT, PAYMENT_STATUS_TEXT, deliveryLabel, needInstall
} = require('../utils/constants');

const delay = (ms) => new Promise((r) => setTimeout(r, ms || 60));
const pad = (n) => (n < 10 ? '0' + n : '' + n);
const now = () => {
  const d = new Date();
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
    pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
};
const todayStr = () => {
  const d = new Date();
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
};

const FAULT_TEXT = {
  THREAD_BREAK: '断线 / 跳针', NOISE: '噪音异响', OIL: '不上油',
  ELECTRIC: '电控故障', FEED: '送布异常', OTHER: '其他', INSTALL: '新机安装'
};
const CUSTOMER_GEO = { latitude: 30.27415, longitude: 120.15515 };

const PRODUCTS = {
  101: {
    id: 101, name: '电脑平缝机 A5', model: 'A5-5000', coverImage: '', price: 328000,
    depositRate: 30, stock: 12, tags: ['含上门安装', '现货'], categoryId: 2, kind: 'MACHINE',
    subtitle: '直驱 · 自动剪线 · 5000 针/分', brand: '标准牌',
    warranty: { months: 12, motorMonths: 24, desc: '整机 12 个月，电机电控 24 个月' },
    services: ['可选上门安装', '可开增值税专用发票'],
    specs: [
      { name: '最高转速', value: '5000 针/分' },
      { name: '适用场景', value: '服装厂流水线 / 加工坊' }
    ],
    skus: [
      { id: 1011, name: '标准台板 + 伺服电机', specs: { 台板: '标准', 电机: '伺服' }, price: 328000, stock: 8 },
      { id: 1012, name: '加厚台板 + 伺服电机', specs: { 台板: '加厚', 电机: '伺服' }, price: 336000, stock: 4 },
      { id: 1013, name: '标准台板 + 直驱电机', specs: { 台板: '标准', 电机: '直驱' }, price: 354000, stock: 0 }
    ]
  },
  102: {
    id: 102, name: '家用多功能机 M3', model: 'M3', coverImage: '', price: 158000,
    depositRate: 30, stock: 20, tags: ['含上门教学'], categoryId: 1, kind: 'MACHINE',
    subtitle: '锁边 · 绣花 · 家用', brand: '标准牌',
    warranty: { months: 12, motorMonths: 24, desc: '整机 12 个月' },
    services: ['可选上门教学'],
    specs: [{ name: '适用场景', value: '家庭 / 裁缝店' }],
    skus: [{ id: 1021, name: '标准套装', specs: { 套装: '标准' }, price: 158000, stock: 20 }]
  },
  103: {
    id: 103, name: '四线包缝机 E4', model: 'E4-7000', coverImage: '', price: 690000,
    depositRate: 30, stock: 3, tags: ['现货 3 台'], categoryId: 3, kind: 'MACHINE',
    subtitle: '超高速 · 低噪', brand: '标准牌',
    warranty: { months: 12, motorMonths: 24, desc: '整机 12 个月' },
    services: ['可选上门安装'],
    specs: [{ name: '最高转速', value: '7000 针/分' }],
    skus: [{ id: 1031, name: '标准配置', specs: { 配置: '标准' }, price: 690000, stock: 3 }]
  },
  201: {
    id: 201, name: '工业平缝机旋梭', model: 'PART-HOOK', coverImage: '', price: 6800,
    depositRate: 100, stock: 50, tags: ['配件'], categoryId: 5, kind: 'PART',
    subtitle: '通用旋梭 · 易耗件', brand: '标准牌',
    warranty: { months: 3, motorMonths: 0, desc: '配件质保 3 个月' },
    services: ['支持快递'],
    specs: [{ name: '适用', value: '平缝机系列' }],
    skus: [{ id: 2011, name: '标准旋梭', specs: { 规格: '标准' }, price: 6800, stock: 50 }]
  },
  202: {
    id: 202, name: '平缝机机针（10 支）', model: 'PART-NEEDLE', coverImage: '', price: 1200,
    depositRate: 100, stock: 200, tags: ['配件'], categoryId: 5, kind: 'PART',
    subtitle: '14 号工业机针', brand: '标准牌',
    warranty: { months: 0, motorMonths: 0, desc: '易耗品无保修' },
    services: ['支持快递'],
    specs: [{ name: '包装', value: '10 支/包' }],
    skus: [{ id: 2021, name: '14 号', specs: { 号数: '14' }, price: 1200, stock: 200 }]
  }
};

const TECHS = [
  {
    id: 88, name: '王师傅', avatar: '', rating: 4.9, orderCount: 186, distanceKm: 2.4,
    skills: ['平缝', '包缝', '电控调试'], intro: '8 年安装经验', recommended: true,
    freeSlots: [], slots: []
  },
  {
    id: 89, name: '李师傅', avatar: '', rating: 4.7, orderCount: 243, distanceKm: 5.1,
    skills: ['绣花机', '特种机'], intro: '擅长特种机', recommended: false,
    freeSlots: [], slots: []
  },
  {
    id: 90, name: '赵师傅', avatar: '', rating: 4.6, orderCount: 97, distanceKm: 8.7,
    skills: ['家用机', '安装教学'], intro: '家用机培训', recommended: false,
    freeSlots: [], slots: []
  }
];

(function fillSlots() {
  const today = todayStr();
  TECHS.forEach((t) => {
    t.freeSlots = [];
    for (let i = 1; i <= 14; i++) {
      const d = new Date();
      d.setDate(d.getDate() + i);
      const date = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
      const text = pad(d.getMonth() + 1) + '-' + pad(d.getDate());
      ['AM', 'PM'].forEach((slot) => {
        t.freeSlots.push({ date, slot, text: text + (slot === 'AM' ? ' 上午' : ' 下午') });
      });
    }
    const soon = t.freeSlots.filter((s) => s.date >= today).slice(0, 3);
    t.slots = [{ text: '已约满', free: false }].concat(soon.map((s) => ({ text: s.text, free: true })));
  });
})();

let ADDRESSES = [
  { id: 55801, name: '张建国', mobile: '13812348000', province: '浙江省', city: '杭州市', district: '余杭区', detail: '某某街道 128 号 3 楼', isDefault: true },
  { id: 55802, name: '张建国', mobile: '13812348000', province: '浙江省', city: '杭州市', district: '萧山区', detail: '某某工业园 B 栋', isDefault: false }
];

const ASSETS = [
  { id: 7781, model: '电脑平缝机 A5', brand: '标准牌', sn: 'A5-2024-008671', buyDate: '2024-05-20', warrantyEnd: '2026-05-20', warrantyStatus: 'IN', warrantyText: '保内，至 2026-05-20', lastServiceAt: '2025-11-23', source: 'INTERNAL' },
  { id: 7782, model: '四线包缝机 E4', brand: '标准牌', sn: 'E4-2023-002114', buyDate: '2023-03-11', warrantyEnd: '2024-03-11', warrantyStatus: 'OUT', warrantyText: '已过保', lastServiceAt: '', source: 'INTERNAL' }
];

let seq = { order: 3, work: 20, cart: 3, payment: 10, ship: 5, repair: 3, asset: 7782, addr: 55802 };
const CART = [];
const ORDERS = {};
const PAYMENTS = {};
const SHIPMENTS = {};
const REPAIRS = {};
const WORKORDERS = {};
let phoneBound = false;

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

function seed() {
  // 购物车样例：整机 + 零件
  CART.push(
    { id: 1, skuId: 1011, quantity: 1, deliveryMethod: DeliveryMethod.ON_SITE_INSTALL, selected: true, productId: 101, name: '电脑平缝机 A5', skuName: '标准台板 + 伺服电机', price: 328000, stock: 8, kind: 'MACHINE' },
    { id: 2, skuId: 2011, quantity: 2, deliveryMethod: DeliveryMethod.EXPRESS, selected: true, productId: 201, name: '工业平缝机旋梭', skuName: '标准旋梭', price: 6800, stock: 50, kind: 'PART' }
  );

  // 待付款样例（管理员已拆定金+尾款）
  const o1 = buildOrder({
    id: 910233001,
    orderNo: 'SO2025112400001',
    status: 'PENDING_PAYMENT',
    items: [
      lineOf(1011, 1, DeliveryMethod.ON_SITE_INSTALL, { installDate: '2026-11-28', installSlot: 'AM', technicianId: 88 }),
      lineOf(2021, 3, DeliveryMethod.EXPRESS)
    ],
    priceAdjusted: true,
    adjustAmount: -20000,
    freightAmount: 0,
    payments: [
      { type: 'DEPOSIT', title: '定金', amount: 100000, status: 'WAIT' },
      { type: 'BALANCE', title: '尾款', amount: 208400, status: 'WAIT' }
    ]
  });
  ORDERS[o1.id] = o1;

  // 已付款履约中 + 发货凭证
  const o2 = buildOrder({
    id: 910233002,
    orderNo: 'SO2025112000002',
    status: 'FULFILLING',
    items: [
      lineOf(1031, 1, DeliveryMethod.HOME_DELIVERY),
      lineOf(2011, 1, DeliveryMethod.EXPRESS)
    ],
    payments: [{ type: 'FULL', title: '全款', amount: 696800, status: 'SUCCESS' }]
  });
  o2.paidAmount = o2.payableAmount;
  ORDERS[o2.id] = o2;
  addShipment(o2, o2.items[0], {
    method: DeliveryMethod.HOME_DELIVERY, status: 'DISPATCHED',
    courierName: '王师傅', courierMobile: '13812348000', appointAt: '2026-11-25 14:00:00'
  });
  addShipment(o2, o2.items[1], {
    method: DeliveryMethod.EXPRESS, status: 'DISPATCHED',
    carrier: '顺丰', trackingNo: 'SF1234567890'
  });

  // 报修：多行 + 保外报价样例
  REPAIRS[88001] = {
    id: 88001, ticketNo: 'RT202511240001', status: 'QUOTED',
    expectDate: '2026-11-26', expectSlot: 'AM',
    quoteAmount: 45000, payableAmount: 45000, paidAmount: 0,
    createdAt: '2025-11-20 11:20:00',
    lines: [
      {
        id: 1, lineNo: 1, assetId: 7782, brand: '标准牌', model: '四线包缝机 E4', sn: 'E4-2023-002114',
        source: 'INTERNAL', faultType: 'NOISE', faultDesc: '高速异响', images: ['mock1'],
        lineQuoteAmount: 45000, inWarranty: false
      },
      {
        id: 2, lineNo: 2, assetId: 0, brand: '某某牌', model: '家用机 X1', sn: '',
        source: 'EXTERNAL', faultType: 'THREAD_BREAK', faultDesc: '断线频繁', images: ['mock2'],
        purchaseChannel: '某电商平台', lineQuoteAmount: 0, inWarranty: false
      }
    ],
    quoteItems: [
      { name: '上门检测费', amount: 8000 },
      { name: '工时费', amount: 12000 },
      { name: '旋梭', amount: 25000 }
    ],
    payments: [],
    timeline: [
      { status: 'SUBMITTED', title: '提交报修', desc: '2 台设备', time: '2025-11-20 11:20:00', done: true, current: false },
      { status: 'QUOTED', title: '管理员已报价', desc: '合计 ¥450', time: '2025-11-20 16:02:00', done: true, current: true }
    ]
  };

  WORKORDERS[660012] = {
    id: 660012, workNo: 'WO20251123000045', type: 'REPAIR', status: 'PENDING',
    faultType: 'THREAD_BREAK', faultDesc: '高速断线', inWarranty: true,
    warrantyDesc: '保内', quoteAmount: 0, quoteConfirmed: true,
    appointmentAt: '', createdAt: now(), customerName: '张建国',
    address: '余杭区某某街道 128 号 3 楼', distanceKm: 2.4,
    technician: null, images: [], latitude: CUSTOMER_GEO.latitude, longitude: CUSTOMER_GEO.longitude,
    timeline: [
      { status: 'PENDING', title: '待接单', desc: '', time: now(), done: true, current: true }
    ]
  };
}
seed();

function lineOf(skuId, qty, method, extra) {
  const found = findSku(skuId);
  const price = found.sku.price;
  const row = {
    id: 0,
    skuId: found.sku.id,
    name: found.product.name,
    skuName: found.sku.name,
    coverImage: '',
    price: price,
    unitPrice: price,
    quantity: qty,
    lineAmount: price * qty,
    deliveryMethod: method,
    deliveryMethodText: deliveryLabel(method),
    installFee: 0,
    installFeeText: needInstall(method) ? '待管理员确认' : '',
    installDate: (extra && extra.installDate) || '',
    installSlot: (extra && extra.installSlot) || '',
    technicianId: (extra && extra.technicianId) || null,
    technicianName: ''
  };
  if (row.technicianId) {
    const t = TECHS.filter((x) => x.id === row.technicianId)[0];
    row.technicianName = t ? t.name : '';
  }
  return row;
}

function buildOrder(opts) {
  const items = (opts.items || []).map((it, i) => Object.assign({}, it, { id: i + 1 }));
  const goods = items.reduce((s, it) => s + it.lineAmount, 0);
  const install = items.reduce((s, it) => s + (it.installFee || 0), 0);
  const freight = opts.freightAmount || 0;
  const adjust = opts.adjustAmount || 0;
  const payable = goods + install + freight + adjust;
  const id = opts.id;
  const payments = (opts.payments || [{ type: 'FULL', title: '全款', amount: payable, status: 'WAIT' }]).map((p, i) => {
    seq.payment += 1;
    const pid = 5000 + seq.payment;
    const row = {
      id: pid, paymentNo: 'PAY' + pid, bizType: 'ORDER', bizId: id,
      channel: 'OFFLINE', type: p.type, title: p.title || PAYMENT_TYPE_TEXT[p.type] || p.type,
      amount: p.amount, status: p.status || 'WAIT', sortNo: i + 1,
      statusText: PAYMENT_STATUS_TEXT[p.status || 'WAIT'],
      typeText: PAYMENT_TYPE_TEXT[p.type] || p.type,
      paidAt: p.status === 'SUCCESS' ? now() : '', offlineIntent: false
    };
    PAYMENTS[pid] = row;
    return row;
  });
  const paid = payments.filter((p) => p.status === 'SUCCESS').reduce((s, p) => s + p.amount, 0);
  return {
    id, orderNo: opts.orderNo, status: opts.status,
    statusText: ORDER_STATUS_TEXT[opts.status] || opts.status,
    goodsAmount: goods, installAmount: install, freightAmount: freight,
    adjustAmount: adjust, payableAmount: payable, paidAmount: paid,
    priceAdjusted: !!opts.priceAdjusted,
    createdAt: opts.createdAt || now(),
    remark: opts.remark || '',
    address: opts.address || ADDRESSES[0],
    items, payments,
    timeline: timelineOf(opts.status),
    actions: actionsOf(opts.status, payments),
    waitPayments: payments.filter((p) => p.status === 'WAIT')
  };
}

function timelineOf(status) {
  const seqArr = ['PENDING_PAYMENT', 'PAID', 'FULFILLING', 'COMPLETED'];
  const texts = {
    PENDING_PAYMENT: ['提交订单', '按标价生成待付款，可线下转账'],
    PAID: ['已付清', '等待发货或派安装'],
    FULFILLING: ['履约中', '发货 / 安装进行中'],
    COMPLETED: ['已完成', '全部履约完成']
  };
  let idx = seqArr.indexOf(status);
  if (status === 'PARTIALLY_PAID') idx = 0;
  if (status === 'CANCELLED') {
    return [{ status: 'CANCELLED', title: '已取消', desc: '', time: now(), done: true, current: true }];
  }
  return seqArr.map((s, i) => ({
    status: s, title: texts[s][0], desc: texts[s][1],
    time: i <= idx ? now() : '',
    done: i <= idx, current: i === idx
  }));
}

function actionsOf(status, payments) {
  const wait = (payments || []).filter((p) => p.status === 'WAIT');
  if (status === 'PENDING_PAYMENT' || status === 'PARTIALLY_PAID') {
    return wait.length ? ['OFFLINE_PAY', 'CANCEL', 'CONTACT_ADMIN'] : ['CANCEL', 'CONTACT_ADMIN'];
  }
  if (status === 'PAID' || status === 'FULFILLING') return ['CONTACT_ADMIN', 'VIEW_SHIPMENT'];
  if (status === 'COMPLETED') return ['REPAIR', 'EVALUATE'];
  return [];
}

function addShipment(order, item, fields) {
  seq.ship += 1;
  const id = 7000 + seq.ship;
  const s = Object.assign({
    id, orderId: order.id, orderItemId: item.id,
    productName: item.name, skuName: item.skuName,
    method: item.deliveryMethod, methodText: deliveryLabel(item.deliveryMethod),
    status: 'PENDING', carrier: '', trackingNo: '', logisticsCompany: '', freightNo: '',
    vehicleNo: '', courierName: '', courierMobile: '', appointAt: '',
    pickupCode: '', pickupStoreId: 0, pickedAt: '', remark: ''
  }, fields);
  s.methodText = deliveryLabel(s.method);
  SHIPMENTS[id] = s;
  item.shipmentId = id;
  return s;
}

function refreshOrderPayments(o) {
  o.payments = Object.keys(PAYMENTS).map((k) => PAYMENTS[k]).filter((p) => p.bizType === 'ORDER' && p.bizId === o.id)
    .sort((a, b) => a.sortNo - b.sortNo);
  o.waitPayments = o.payments.filter((p) => p.status === 'WAIT');
  o.paidAmount = o.payments.filter((p) => p.status === 'SUCCESS').reduce((s, p) => s + p.amount, 0);
  o.actions = actionsOf(o.status, o.payments);
  o.statusText = ORDER_STATUS_TEXT[o.status] || o.status;
  o.timeline = timelineOf(o.status);
}

function cartView() {
  return {
    items: CART.map((c) => Object.assign({}, c, {
      priceText: c.price,
      lineAmount: c.price * c.quantity,
      deliveryMethodText: deliveryLabel(c.deliveryMethod),
      needInstall: needInstall(c.deliveryMethod)
    })),
    totalQuantity: CART.reduce((s, c) => s + (c.selected ? c.quantity : 0), 0),
    selectedAmount: CART.reduce((s, c) => s + (c.selected ? c.price * c.quantity : 0), 0)
  };
}

const ok = (data) => ({ statusCode: 200, body: { code: 0, msg: 'ok', data, traceId: 'mock' } });
const fail = (code, msg) => ({ statusCode: 200, body: { code, msg, data: null, traceId: 'mock' } });

const ROUTES = [
  ['POST', /^\/api\/auth\/login$/, () => ok({
    token: 'mock-token', expiresIn: 2592000, hasMobile: phoneBound,
    customer: { id: 1001, nickname: '张建国', avatar: '', mobile: phoneBound ? '138****8000' : '', level: 3, levelText: '金卡客户' }
  })],
  ['POST', /^\/api\/auth\/phone$/, () => { phoneBound = true; return ok({ mobile: '138****8000' }); }],

  ['GET', /^\/api\/home$/, () => ok({
    banners: [{ id: 1, image: '', title: '新机到家，师傅上门', subtitle: '整机零件可同车结算 · 一期线下转账', linkType: 'PRODUCT_LIST', linkValue: '0' }],
    quickEntries: [
      { code: 'BUY', title: '买机器', icon: '机', badge: '' },
      { code: 'REPAIR', title: '一键报修', icon: '修', badge: '' },
      { code: 'ASSET', title: '我的设备', icon: '档', badge: String(ASSETS.length) },
      { code: 'CART', title: '购物车', icon: '车', badge: String(CART.length) }
    ],
    categories: [
      { id: 1, name: '家用多功能', count: 1 }, { id: 2, name: '工业平缝', count: 1 },
      { id: 3, name: '包缝绷缝', count: 1 }, { id: 5, name: '配件耗材', count: 2 }
    ],
    hotProducts: [101, 102, 103, 201].map((id) => {
      const p = PRODUCTS[id];
      return { id: p.id, name: p.name, model: p.model, coverImage: '', price: p.price, depositAmount: Math.floor(p.price * p.depositRate / 100), stock: p.stock, tags: p.tags, brand: p.brand, kind: p.kind };
    })
  })],

  ['GET', /^\/api\/categories$/, () => ok([
    { id: 1, name: '家用多功能', count: 1 }, { id: 2, name: '工业平缝', count: 1 },
    { id: 3, name: '包缝绷缝', count: 1 }, { id: 5, name: '配件耗材', count: 2 }
  ])],

  ['GET', /^\/api\/products$/, (m, path, q) => {
    let list = Object.keys(PRODUCTS).map((k) => PRODUCTS[k]);
    if (q && q.categoryId) list = list.filter((p) => String(p.categoryId) === String(q.categoryId));
    if (q && q.keyword) {
      const kw = String(q.keyword).toLowerCase();
      list = list.filter((p) => p.name.toLowerCase().indexOf(kw) >= 0 || p.model.toLowerCase().indexOf(kw) >= 0);
    }
    list = list.map((p) => ({
      id: p.id, name: p.name, model: p.model, coverImage: '', price: p.price, brand: p.brand, kind: p.kind,
      depositAmount: Math.floor(p.price * p.depositRate / 100), stock: p.stock, tags: p.tags
    }));
    if (q && q.sort === 'price_asc') list.sort((a, b) => a.price - b.price);
    if (q && q.sort === 'price_desc') list.sort((a, b) => b.price - a.price);
    return ok({ total: list.length, page: 1, size: 20, list });
  }],

  ['GET', /^\/api\/products\/(\d+)$/, (m, path, q, id) => {
    const p = PRODUCTS[id];
    if (!p) return fail(1001, '机型不存在');
    const data = Object.assign({}, p, {
      depositAmount: Math.floor(p.price * p.depositRate / 100),
      images: [], videoUrl: '',
      skus: p.skus.map((s) => Object.assign({}, s, { depositAmount: Math.floor(s.price * p.depositRate / 100) }))
    });
    return ok(data);
  }],

  ['GET', /^\/api\/technicians\/available$/, (m, path, q) => {
    let list = TECHS.map((t) => Object.assign({}, t, { freeSlots: t.freeSlots.slice(), slots: t.slots.slice() }));
    const sort = (q && q.sort) || 'recommend';
    if (sort === 'distance') list.sort((a, b) => a.distanceKm - b.distanceKm);
    else if (sort === 'rating') list.sort((a, b) => b.rating - a.rating);
    else list.sort((a, b) => (b.recommended === true) - (a.recommended === true) || a.distanceKm - b.distanceKm);
    if (q && q.date) {
      list = list.map((t) => Object.assign({}, t, {
        bookable: (t.freeSlots || []).some((s) => s.date === q.date)
      }));
    }
    return ok(list);
  }],

  /* -------- 购物车 -------- */
  ['GET', /^\/api\/cart$/, () => ok(cartView())],

  ['POST', /^\/api\/cart\/items$/, (m, path, body) => {
    const found = findSku(body.skuId);
    if (!found.sku) return fail(1001, 'SKU 不存在');
    if (found.sku.stock <= 0) return fail(3001, '暂时缺货');
    const method = body.deliveryMethod || (found.product.kind === 'PART' ? DeliveryMethod.EXPRESS : DeliveryMethod.ON_SITE_INSTALL);
    const exist = CART.filter((c) => c.skuId === Number(body.skuId) && c.deliveryMethod === method)[0];
    const qty = Number(body.quantity || 1);
    if (exist) {
      exist.quantity = Math.min(99, exist.quantity + qty);
      exist.selected = true;
      return ok({ id: exist.id });
    }
    seq.cart += 1;
    const row = {
      id: seq.cart, skuId: found.sku.id, quantity: qty, deliveryMethod: method, selected: true,
      productId: found.product.id, name: found.product.name, skuName: found.sku.name,
      price: found.sku.price, stock: found.sku.stock, kind: found.product.kind
    };
    CART.push(row);
    return ok({ id: row.id });
  }],

  ['PUT', /^\/api\/cart\/items\/(\d+)$/, (m, path, body, id) => {
    const row = CART.filter((c) => c.id === Number(id))[0];
    if (!row) return fail(1001, '购物车项不存在');
    if (body.quantity !== undefined) {
      const q = Number(body.quantity);
      if (q <= 0) {
        const idx = CART.findIndex((c) => c.id === row.id);
        CART.splice(idx, 1);
        return ok(true);
      }
      row.quantity = Math.min(99, q);
    }
    if (body.deliveryMethod) row.deliveryMethod = body.deliveryMethod;
    if (body.selected !== undefined) row.selected = !!body.selected;
    return ok(true);
  }],

  ['DELETE', /^\/api\/cart\/items\/(\d+)$/, (m, path, body, id) => {
    const idx = CART.findIndex((c) => c.id === Number(id));
    if (idx < 0) return fail(1001, '购物车项不存在');
    CART.splice(idx, 1);
    return ok(true);
  }],

  /* -------- 订单 -------- */
  ['POST', /^\/api\/orders$/, (m, path, body) => {
    if (body.requestId) {
      const hit = Object.keys(ORDERS).map((k) => ORDERS[k]).filter((o) => o.requestId === body.requestId)[0];
      if (hit) {
        return ok({
          orderId: hit.id, orderNo: hit.orderNo, payableAmount: hit.payableAmount,
          status: hit.status, paymentId: (hit.payments[0] && hit.payments[0].id) || 0
        });
      }
    }
    const itemsIn = body.items || [];
    if (!itemsIn.length) return fail(1001, '请至少选择一件商品');
    const lines = [];
    for (let i = 0; i < itemsIn.length; i++) {
      const it = itemsIn[i];
      const found = findSku(it.skuId);
      if (!found.sku) return fail(1001, 'SKU 不存在: ' + it.skuId);
      if (found.sku.stock < Number(it.quantity || 1)) return fail(3001, found.product.name + ' 库存不足');
      const method = it.deliveryMethod || DeliveryMethod.EXPRESS;
      if (needInstall(method)) {
        if (!it.installDate || !it.installSlot) return fail(1001, '上门安装请选择日期与时段');
        if (it.technicianId) {
          const tech = TECHS.filter((t) => t.id === Number(it.technicianId))[0];
          if (!tech) return fail(3002, '师傅档期不可用');
        }
      }
      const line = lineOf(found.sku.id, Number(it.quantity || 1), method, it);
      line.id = i + 1;
      lines.push(line);
    }
    const addr = ADDRESSES.filter((a) => a.id === Number(body.addressId))[0] || ADDRESSES[0];
    if (!addr) return fail(1001, '请选择地址');
    seq.order += 1;
    const id = 910233000 + seq.order;
    const goods = lines.reduce((s, it) => s + it.lineAmount, 0);
    seq.payment += 1;
    const payId = 5000 + seq.payment;
    const payment = {
      id: payId, paymentNo: 'PAY' + payId, bizType: 'ORDER', bizId: id,
      channel: 'OFFLINE', type: 'FULL', title: '全款', amount: goods, status: 'WAIT',
      sortNo: 1, statusText: '待付款', typeText: '全款', paidAt: '', offlineIntent: false
    };
    PAYMENTS[payId] = payment;
    const o = {
      id, requestId: body.requestId, orderNo: 'SO' + todayStr().replace(/-/g, '') + String(seq.order).padStart(4, '0'),
      status: 'PENDING_PAYMENT', statusText: '待付款',
      goodsAmount: goods, installAmount: 0, freightAmount: 0, adjustAmount: 0,
      payableAmount: goods, paidAmount: 0, priceAdjusted: false,
      createdAt: now(), remark: body.remark || '', address: addr,
      items: lines, payments: [payment], waitPayments: [payment],
      timeline: timelineOf('PENDING_PAYMENT'), actions: actionsOf('PENDING_PAYMENT', [payment])
    };
    ORDERS[id] = o;
    // 结算后从购物车移除对应 SKU
    (body.cartItemIds || []).forEach((cid) => {
      const idx = CART.findIndex((c) => c.id === Number(cid));
      if (idx >= 0) CART.splice(idx, 1);
    });
    if (!body.cartItemIds) {
      lines.forEach((line) => {
        const idx = CART.findIndex((c) => c.skuId === line.skuId && c.selected);
        if (idx >= 0) CART.splice(idx, 1);
      });
    }
    return ok({
      orderId: id, orderNo: o.orderNo, payableAmount: goods,
      status: 'PENDING_PAYMENT', paymentId: payId
    });
  }],

  ['GET', /^\/api\/orders$/, (m, path, q) => {
    let list = Object.keys(ORDERS).map((k) => ORDERS[k]);
    if (q && q.status && q.status !== 'ALL') {
      if (q.status === 'AFTER_SALE') list = [];
      else list = list.filter((o) => o.status === q.status);
    }
    list.sort((a, b) => b.id - a.id);
    return ok({
      total: list.length, page: 1, size: 20,
      list: list.map((o) => ({
        id: o.id, orderNo: o.orderNo, status: o.status, statusText: o.statusText,
        totalAmount: o.payableAmount, payableAmount: o.payableAmount,
        paidAmount: o.paidAmount, goodsAmount: o.goodsAmount,
        createdAt: o.createdAt, items: o.items,
        waitAmount: (o.waitPayments || []).reduce((s, p) => s + p.amount, 0)
      }))
    });
  }],

  ['GET', /^\/api\/orders\/(\d+)$/, (m, path, q, id) => {
    const o = ORDERS[id];
    if (!o) return fail(1001, '订单不存在');
    refreshOrderPayments(o);
    return ok(Object.assign({}, o, {
      depositAmount: (o.payments.filter((p) => p.type === 'DEPOSIT')[0] || {}).amount || 0,
      balanceAmount: (o.waitPayments || []).reduce((s, p) => s + p.amount, 0)
    }));
  }],

  ['POST', /^\/api\/orders\/(\d+)\/cancel$/, (m, path, body, id) => {
    const o = ORDERS[id];
    if (!o) return fail(1001, '订单不存在');
    if (o.status !== 'PENDING_PAYMENT' && o.status !== 'PARTIALLY_PAID') {
      return fail(3003, '当前状态不可取消');
    }
    if (o.payments.some((p) => p.status === 'SUCCESS')) return fail(3003, '已有成功付款，请联系管理员');
    o.status = 'CANCELLED';
    o.payments.forEach((p) => {
      if (p.status === 'WAIT') {
        p.status = 'CLOSED';
        p.statusText = '已关闭';
        PAYMENTS[p.id] = p;
      }
    });
    refreshOrderPayments(o);
    return ok(true);
  }],

  ['GET', /^\/api\/orders\/(\d+)\/shipments$/, (m, path, q, id) => {
    const list = Object.keys(SHIPMENTS).map((k) => SHIPMENTS[k]).filter((s) => String(s.orderId) === String(id));
    return ok(list);
  }],

  ['POST', /^\/api\/orders\/(\d+)\/accept$/, (m, path, body, id) => {
    const o = ORDERS[id];
    if (!o) return fail(1001, '订单不存在');
    if (body.pass === false) return ok({ status: o.status });
    if (o.status === 'FULFILLING' || o.status === 'PAID') {
      o.status = 'COMPLETED';
      refreshOrderPayments(o);
    }
    return ok({ status: o.status });
  }],

  /* -------- 支付 -------- */
  ['POST', /^\/api\/pay\/offline\/intent$/, (m, path, body) => {
    const bizId = Number(body.bizId || body.orderId);
    let payments = Object.keys(PAYMENTS).map((k) => PAYMENTS[k])
      .filter((p) => p.bizId === bizId && p.status === 'WAIT');
    if (body.paymentId) payments = payments.filter((p) => p.id === Number(body.paymentId));
    if (!payments.length) return fail(1001, '没有待付款');
    payments.forEach((p) => { p.offlineIntent = true; p.intentRemark = body.remark || ''; p.intentAt = now(); });
    return ok({ ok: true, count: payments.length, tip: '已通知管理员，请按约定线下转账' });
  }],

  ['POST', /^\/api\/pay\/prepay$/, () => fail(5001, '微信支付为二期能力，一期请线下转账')],

  ['POST', /^\/api\/pay\/mock-success$/, (m, path, body) => {
    // 仅 Mock 演示「管理员登记收款」后的效果，供测试推进状态；正式客户端不调用
    const bizId = Number(body.orderId || body.bizId);
    const o = ORDERS[bizId];
    if (o) {
      let targets = o.payments.filter((p) => p.status === 'WAIT');
      if (body.paymentId) targets = targets.filter((p) => p.id === Number(body.paymentId));
      if (body.type) targets = targets.filter((p) => p.type === body.type) || targets;
      targets.forEach((p) => {
        p.status = 'SUCCESS';
        p.statusText = '已入账';
        p.paidAt = now();
        PAYMENTS[p.id] = p;
      });
      refreshOrderPayments(o);
      if (!o.waitPayments.length) {
        o.status = 'PAID';
        // 上门安装行生成工单
        o.items.filter((it) => needInstall(it.deliveryMethod)).forEach((it) => {
          seq.work += 1;
          const wid = 660000 + seq.work;
          WORKORDERS[wid] = {
            id: wid, workNo: 'WO' + todayStr().replace(/-/g, '') + String(seq.work).padStart(4, '0'),
            type: 'INSTALL', status: 'PENDING', faultType: 'INSTALL',
            faultDesc: it.name + ' · ' + it.skuName, inWarranty: true,
            warrantyDesc: '新机安装', quoteAmount: 0, quoteConfirmed: true,
            appointmentAt: (it.installDate || '') + (it.installSlot === 'PM' ? ' 14:00:00' : ' 09:30:00'),
            createdAt: now(), customerName: o.address.name, address: (o.address.province || '') + (o.address.city || '') + (o.address.district || '') + (o.address.detail || ''),
            distanceKm: 2.4, technician: it.technicianId ? { id: it.technicianId, name: it.technicianName, mobile: '13812348000', rating: 4.9 } : null,
            images: [], orderId: o.id, latitude: CUSTOMER_GEO.latitude, longitude: CUSTOMER_GEO.longitude,
            timeline: [{ status: 'PENDING', title: '待接单', desc: '', time: now(), done: true, current: true }]
          };
        });
        refreshOrderPayments(o);
      } else {
        o.status = 'PARTIALLY_PAID';
        refreshOrderPayments(o);
      }
      return ok({ status: o.status });
    }
    const r = REPAIRS[bizId];
    if (r) {
      r.status = 'PAID';
      r.paidAmount = r.payableAmount;
      return ok({ status: r.status });
    }
    return fail(1001, '单据不存在');
  }],

  ['GET', /^\/api\/contact\/admin$/, () => ok({
    name: '店长小周',
    wechat: 'sewing-admin',
    phone: '0571-88886666',
    tip: '下单后请联系管理员确认发货与转账账户；安装费由管理员核定。'
  })],

  /* -------- 客户 -------- */
  ['GET', /^\/api\/customer\/profile$/, () => {
    const pending = Object.keys(ORDERS).map((k) => ORDERS[k])
      .reduce((s, o) => s + (o.waitPayments || []).reduce((x, p) => x + p.amount, 0), 0);
    return ok({
      customer: { id: 1001, nickname: '张建国', avatar: '', mobile: '138****8000', level: 3, levelText: '金卡客户' },
      orderCount: Object.keys(ORDERS).length, totalPaid: 4260000,
      pendingAmount: pending, assetCount: ASSETS.length, couponCount: 2
    });
  }],
  ['GET', /^\/api\/customer\/addresses$/, () => ok(ADDRESSES)],
  ['POST', /^\/api\/customer\/addresses$/, (m, path, body) => {
    seq.addr += 1;
    const row = {
      id: seq.addr, name: body.name || '', mobile: body.mobile || '',
      province: body.province || '', city: body.city || '', district: body.district || '',
      detail: body.detail || '', isDefault: !!body.isDefault
    };
    if (row.isDefault) ADDRESSES.forEach((a) => { a.isDefault = false; });
    if (!ADDRESSES.length) row.isDefault = true;
    ADDRESSES.push(row);
    return ok({ id: row.id });
  }],
  ['PUT', /^\/api\/customer\/addresses\/(\d+)$/, (m, path, body, id) => {
    const row = ADDRESSES.filter((a) => a.id === Number(id))[0];
    if (!row) return fail(1001, '地址不存在');
    ['name', 'mobile', 'province', 'city', 'district', 'detail'].forEach((k) => {
      if (body[k] !== undefined) row[k] = body[k];
    });
    if (body.isDefault) {
      ADDRESSES.forEach((a) => { a.isDefault = false; });
      row.isDefault = true;
    }
    return ok(true);
  }],
  ['DELETE', /^\/api\/customer\/addresses\/(\d+)$/, (m, path, body, id) => {
    const idx = ADDRESSES.findIndex((a) => a.id === Number(id));
    if (idx < 0) return fail(1001, '地址不存在');
    const was = ADDRESSES[idx].isDefault;
    ADDRESSES.splice(idx, 1);
    if (was && ADDRESSES[0]) ADDRESSES[0].isDefault = true;
    return ok(true);
  }],

  ['GET', /^\/api\/assets$/, () => ok(ASSETS)],
  ['POST', /^\/api\/assets\/bind$/, (m, path, body) => {
    const hit = ASSETS.filter((a) => a.sn === (body.sn || '').trim())[0];
    return hit ? ok(hit) : fail(1001, '未查询到该机身码');
  }],

  /* -------- 报修一单多行 -------- */
  ['GET', /^\/api\/repairs$/, () => {
    const list = Object.keys(REPAIRS).map((k) => REPAIRS[k]);
    list.sort((a, b) => b.id - a.id);
    return ok({
      total: list.length, page: 1, size: 20,
      list: list.map((r) => ({
        id: r.id, ticketNo: r.ticketNo, status: r.status,
        statusText: REPAIR_STATUS_TEXT[r.status] || r.status,
        quoteAmount: r.quoteAmount, lineCount: (r.lines || []).length,
        createdAt: r.createdAt, expectDate: r.expectDate,
        faultText: (r.lines[0] && FAULT_TEXT[r.lines[0].faultType]) || '报修'
      }))
    });
  }],

  ['POST', /^\/api\/repairs$/, (m, path, body) => {
    const linesIn = body.lines || [];
    if (!linesIn.length) return fail(1001, '请至少添加一台设备');
    const lines = [];
    for (let i = 0; i < linesIn.length; i++) {
      const L = linesIn[i];
      let brand = '', model = '', sn = '', source = 'INTERNAL', assetId = 0, warranty = true;
      if (L.assetId) {
        const asset = ASSETS.filter((a) => a.id === Number(L.assetId))[0];
        if (!asset) return fail(1001, '设备不存在');
        brand = asset.brand; model = asset.model; sn = asset.sn; assetId = asset.id;
        source = asset.source || 'INTERNAL';
        warranty = asset.warrantyStatus === 'IN';
      } else if (L.external) {
        const ext = L.external;
        if (!ext.brand || !ext.model || !ext.faultDesc) return fail(1001, '外购设备须填写品牌、型号、故障描述');
        if (!(ext.images || L.images || []).length) return fail(1001, '外购设备须上传现场照片');
        brand = ext.brand; model = ext.model; sn = ext.sn || '';
        source = 'EXTERNAL';
        seq.asset += 1;
        assetId = seq.asset;
        ASSETS.push({
          id: assetId, model, brand, sn, buyDate: '', warrantyEnd: '',
          warrantyStatus: 'UNKNOWN', warrantyText: '外购设备，保修待核实',
          lastServiceAt: '', source: 'EXTERNAL',
          purchaseChannel: ext.purchaseChannel || '', purchaseProofUrl: ext.purchaseProof || ''
        });
        warranty = false;
      } else {
        return fail(1001, '每行需 assetId 或 external');
      }
      const images = L.images || (L.external && L.external.images) || [];
      if (!images.length) return fail(1001, '请上传故障照片');
      lines.push({
        id: i + 1, lineNo: i + 1, assetId, brand, model, sn, source,
        faultType: L.faultType || 'OTHER',
        faultDesc: L.faultDesc || (L.external && L.external.faultDesc) || '',
        images, purchaseChannel: (L.external && L.external.purchaseChannel) || '',
        purchaseProofUrl: (L.external && (L.external.purchaseProof || L.external.purchaseProofUrl)) || '',
        lineQuoteAmount: 0, inWarranty: warranty
      });
    }
    seq.repair += 1;
    const id = 88000 + seq.repair;
    const allIn = lines.every((l) => l.inWarranty);
    REPAIRS[id] = {
      id, ticketNo: 'RT' + todayStr().replace(/-/g, '') + String(seq.repair).padStart(4, '0'),
      status: 'SUBMITTED', expectDate: body.expectDate || '', expectSlot: body.expectSlot || 'AM',
      quoteAmount: 0, payableAmount: 0, paidAmount: 0, createdAt: now(),
      lines, quoteItems: [], payments: [],
      timeline: [
        { status: 'SUBMITTED', title: '提交报修', desc: lines.length + ' 台设备', time: now(), done: true, current: true },
        { status: 'QUOTED', title: '等待报价', desc: allIn ? '含保内设备，管理员核实后派工' : '保外将生成报价', time: '', done: false, current: false }
      ]
    };
    return ok({
      ticketId: id, id, ticketNo: REPAIRS[id].ticketNo,
      status: 'SUBMITTED', statusText: '已提交', lineCount: lines.length
    });
  }],

  ['GET', /^\/api\/repairs\/(\d+)$/, (m, path, q, id) => {
    const r = REPAIRS[id];
    if (!r) return fail(1001, '报修单不存在');
    return ok(Object.assign({}, r, {
      statusText: REPAIR_STATUS_TEXT[r.status] || r.status,
      typeText: '维修',
      workNo: r.ticketNo,
      faultText: (r.lines[0] && FAULT_TEXT[r.lines[0].faultType]) || '报修',
      inWarranty: (r.lines || []).every((l) => l.inWarranty),
      warrantyDesc: (r.lines || []).every((l) => l.inWarranty) ? '全部保内（待核实）' : '含保外或外购，需报价'
    }));
  }],

  ['POST', /^\/api\/repairs\/(\d+)\/quote\/confirm$/, (m, path, body, id) => {
    const r = REPAIRS[id];
    if (!r) return fail(1001, '报修单不存在');
    if (body.accept === false) {
      r.status = 'CANCELLED';
      return ok({ status: 'CANCELLED' });
    }
    if (r.quoteAmount > 0) {
      r.status = 'PENDING_PAYMENT';
      seq.payment += 1;
      const pid = 5000 + seq.payment;
      const pay = {
        id: pid, paymentNo: 'PAY' + pid, bizType: 'REPAIR', bizId: r.id,
        channel: 'OFFLINE', type: 'REPAIR', title: '维修费', amount: r.quoteAmount,
        status: 'WAIT', sortNo: 1, statusText: '待付款', typeText: '维修费', paidAt: '', offlineIntent: false
      };
      PAYMENTS[pid] = pay;
      r.payments = [pay];
      r.payableAmount = r.quoteAmount;
    } else {
      r.status = 'CONFIRMED';
    }
    return ok({ status: r.status });
  }],

  ['POST', /^\/api\/evaluations$/, () => ok(true)],
  ['GET', /^\/api\/me\/changes$/, () => ok({
    cursor: String(Date.now()),
    changed: false,
    orders: false,
    repairs: false,
    products: false
  })],
  ['GET', /^\/api\/upload\/policy$/, () => ok({
    host: '', dir: 'mock/', expireAt: 0, policy: 'mock', signature: 'mock', accessKeyId: 'mock'
  })],

  /* -------- 师傅端（保留演示） -------- */
  ['GET', /^\/api\/tech\/workorders$/, (m, path, q) => {
    let list = Object.keys(WORKORDERS).map((k) => WORKORDERS[k]);
    if (q && q.status) list = list.filter((w) => w.status === q.status);
    list.sort((a, b) => b.id - a.id);
    return ok({
      total: list.length, page: 1, size: 20,
      list: list.map((w) => ({
        id: w.id, workNo: w.workNo, type: w.type, typeText: w.type === 'INSTALL' ? '安装' : '维修',
        status: w.status, statusText: w.status === 'PENDING' ? '待接单' : w.status,
        faultText: FAULT_TEXT[w.faultType] || '', inWarranty: w.inWarranty,
        appointmentAt: w.appointmentAt, customerName: w.customerName,
        address: w.address, distanceKm: w.distanceKm
      }))
    });
  }],
  ['GET', /^\/api\/tech\/workorders\/(\d+)$/, (m, path, q, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    return ok(Object.assign({}, w, {
      typeText: w.type === 'INSTALL' ? '安装' : '维修',
      statusText: w.status === 'PENDING' ? '待接单' : w.status,
      faultText: FAULT_TEXT[w.faultType] || ''
    }));
  }],
  ['POST', /^\/api\/tech\/workorders\/(\d+)\/accept$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    w.status = 'ACCEPTED';
    if (body && body.appointmentAt) w.appointmentAt = body.appointmentAt;
    return ok({ status: 'ACCEPTED' });
  }],
  ['POST', /^\/api\/tech\/workorders\/(\d+)\/reject$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    w.status = 'CLOSED';
    return ok({ status: 'CLOSED' });
  }],
  ['POST', /^\/api\/tech\/workorders\/(\d+)\/checkin$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    const lat = Number(body && body.latitude);
    const lng = Number(body && body.longitude);
    if (!lat || !lng) return fail(1001, '缺少定位');
    const R = 6371;
    const toRad = (n) => n * Math.PI / 180;
    const dLat = toRad((w.latitude || CUSTOMER_GEO.latitude) - lat);
    const dLon = toRad((w.longitude || CUSTOMER_GEO.longitude) - lng);
    const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat)) * Math.cos(toRad(w.latitude || CUSTOMER_GEO.latitude)) * Math.sin(dLon / 2) ** 2;
    const km = R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    if (km > 0.5) return fail(3005, '距离客户地址超过 500 米，无法打卡');
    w.status = 'ON_SITE';
    return ok({ status: 'ON_SITE', checkinAt: now() });
  }],
  ['POST', /^\/api\/tech\/workorders\/(\d+)\/finish$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    if (w.type === 'INSTALL') {
      if (!body || !body.sn) return fail(1001, '请填写或扫描机身码');
      if (((body && body.images) || []).length < 3) return fail(1001, '安装单至少上传 3 张现场照片');
      if (!body.signImage) return fail(1001, '请让客户签字');
    } else if (!body || !body.remark) {
      return fail(1001, '请填写处理措施');
    }
    w.status = 'DONE';
    return ok({ status: 'DONE', finishAt: now() });
  }]
];

async function request(path, method, data) {
  await delay(40 + Math.random() * 40);
  const mtd = method || 'GET';
  for (let i = 0; i < ROUTES.length; i++) {
    const route = ROUTES[i];
    if (route[0] !== mtd) continue;
    const hit = route[1].exec(path);
    if (hit) return route[2](mtd, path, data || {}, hit[1]);
  }
  return fail(404, 'Mock 未实现该接口: ' + mtd + ' ' + path);
}

module.exports = { request };
