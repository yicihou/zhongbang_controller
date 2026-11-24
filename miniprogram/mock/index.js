/**
 * 本地 Mock 数据层：无需后端即可在开发者工具里跑通客户端与师傅端。
 * config.js 里 USE_MOCK = false 时，request.js 会改走 baseUrl 上的 HTTP。
 *
 * 字段与 api/openapi.yaml 对齐，金额单位为分。
 * 契约里没有、但页面需要的能力会在对应路由上注明（报修列表、工单详情、拒单）。
 */
const delay = (ms) => new Promise((r) => setTimeout(r, ms || 80));
const now = () => {
  const d = new Date();
  const p = (n) => (n < 10 ? '0' + n : '' + n);
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' +
    p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
};

const FAULT_TEXT = {
  THREAD_BREAK: '断线 / 跳针',
  NOISE: '噪音异响',
  OIL: '不上油',
  ELECTRIC: '电控故障',
  FEED: '送布异常',
  OTHER: '其他',
  INSTALL: '新机安装'
};

/* 演示用客户坐标（余杭），打卡按 ≤ 500 米校验 */
const CUSTOMER_GEO = { latitude: 30.27415, longitude: 120.15515 };

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
    ],
    slots: [
      { text: '11-27', free: false },
      { text: '11-28 上午', free: true },
      { text: '11-28 下午', free: true },
      { text: '11-29', free: false }
    ]
  },
  {
    id: 89, name: '李师傅', avatar: '', rating: 4.7, orderCount: 243, distanceKm: 5.1,
    skills: ['绣花机', '特种机'], intro: '擅长绣花机与特种机', recommended: false,
    freeSlots: [
      { date: '2025-11-27', slot: 'AM', text: '11-27 上午' },
      { date: '2025-11-28', slot: 'PM', text: '11-28 下午' }
    ],
    slots: [
      { text: '11-27', free: true },
      { text: '11-28 上午', free: false },
      { text: '11-28 下午', free: true }
    ]
  },
  {
    id: 90, name: '赵师傅', avatar: '', rating: 4.6, orderCount: 97, distanceKm: 8.7,
    skills: ['家用机', '安装教学'], intro: '擅长家用机安装与操作培训', recommended: false,
    freeSlots: [
      { date: '2025-11-27', slot: 'AM', text: '11-27 上午' },
      { date: '2025-11-28', slot: 'AM', text: '11-28 上午' }
    ],
    slots: [
      { text: '11-27', free: true },
      { text: '11-28 上午', free: true },
      { text: '11-29', free: true }
    ]
  }
];

/** 在固定档期之外，补上从明天起的可约时段，避免写死的日期过期后无法下单 */
function fillUpcomingSlots() {
  const p = (n) => (n < 10 ? '0' + n : '' + n);
  const today = new Date();
  const todayStr = today.getFullYear() + '-' + p(today.getMonth() + 1) + '-' + p(today.getDate());
  TECHS.forEach((t) => {
    for (let i = 1; i <= 14; i++) {
      const d = new Date();
      d.setDate(d.getDate() + i);
      const date = d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
      const text = p(d.getMonth() + 1) + '-' + p(d.getDate());
      ['AM', 'PM'].forEach((slot) => {
        const label = text + (slot === 'AM' ? ' 上午' : ' 下午');
        const exists = t.freeSlots.some((s) => s.date === date && s.slot === slot);
        if (!exists) t.freeSlots.push({ date: date, slot: slot, text: label });
      });
    }
    const soon = t.freeSlots.filter((s) => s.date >= todayStr).slice(0, 3);
    t.slots = [{ text: '已约满', free: false }].concat(soon.map((s) => ({ text: s.text, free: true })));
  });
}
fillUpcomingSlots();

let ADDRESSES = [
  { id: 55801, name: '张建国', mobile: '13812348000', province: '浙江省', city: '杭州市', district: '余杭区', detail: '某某街道 128 号 3 楼', isDefault: true },
  { id: 55802, name: '张建国', mobile: '13812348000', province: '浙江省', city: '杭州市', district: '萧山区', detail: '某某工业园 B 栋', isDefault: false }
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
let seq = { order: 2, work: 12, addr: 55802, asset: 7782 };
const ORDERS = {};
const WORKORDERS = {};
const EVALUATED = {};
let phoneBound = false;

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
      technician: { id: tech.id, name: tech.name, mobile: '13812348000', rating: tech.rating },
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

  WORKORDERS[660012] = baseRepair({
    id: 660012, workNo: 'WO20251123000045', status: 'ACCEPTED',
    faultType: 'THREAD_BREAK', faultDesc: '高速运转时断线，低速正常，已换过机针',
    inWarranty: true, warrantyDesc: '整机保修至 2026-05-20，本次故障在保修范围内',
    quoteAmount: 0, quoteConfirmed: true, assetId: 7781, assetModel: '电脑平缝机 A5',
    appointmentAt: '2025-11-25 09:30:00', createdAt: '2025-11-23 20:14:00',
    technician: { id: 89, name: '李师傅', mobile: '13812348000', rating: 4.7 },
    timeline: [
      { status: 'PENDING', title: '提交报修', desc: '已判定保内，等待派单', time: '2025-11-23 20:14:00', done: true, current: false },
      { status: 'ACCEPTED', title: '师傅已接单', desc: '约定 11-25 上午上门', time: '2025-11-24 09:02:00', done: true, current: true },
      { status: 'DONE', title: '维修完成', desc: '', time: '', done: false, current: false }
    ]
  });

  /* 保外待确认报价，方便直接打开报价页 */
  WORKORDERS[660099] = baseRepair({
    id: 660099, workNo: 'WO20251120000099', status: 'ACCEPTED',
    faultType: 'NOISE', faultDesc: '包缝机高速有异响，低速减轻',
    inWarranty: false, warrantyDesc: '已过保。报价超过 300 元，确认后才能施工',
    quoteAmount: 45000, quoteConfirmed: false, assetId: 7782, assetModel: '四线包缝机 E4',
    quoteItems: [
      { name: '上门检测费', amount: 8000 },
      { name: '工时费', amount: 12000 },
      { name: '旋梭', amount: 25000 }
    ],
    appointmentAt: '2025-11-26 14:00:00', createdAt: '2025-11-20 11:20:00',
    technician: { id: 88, name: '王师傅', mobile: '13812348000', rating: 4.9 },
    timeline: [
      { status: 'PENDING', title: '提交报修', desc: '已判定保外', time: '2025-11-20 11:20:00', done: true, current: false },
      { status: 'ACCEPTED', title: '待确认报价', desc: '报价 ¥450，超过 300 元须确认', time: '2025-11-20 16:02:00', done: true, current: true },
      { status: 'DONE', title: '维修完成', desc: '', time: '', done: false, current: false }
    ]
  });
}

function baseRepair(extra) {
  return Object.assign({
    type: 'REPAIR',
    actualAmount: extra.quoteAmount || 0,
    customerName: '张建国',
    address: '浙江省杭州市余杭区某某街道 128 号 3 楼',
    distanceKm: 2.4,
    images: [],
    video: '',
    latitude: CUSTOMER_GEO.latitude,
    longitude: CUSTOMER_GEO.longitude,
    paid: false,
    parts: [],
    remark: ''
  }, extra);
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
    done: idx >= 0 && i <= idx, current: i === idx
  }));
}

function actionsOf(o) {
  if (o.status === 'UNPAID') return ['PAY_DEPOSIT', 'CANCEL'];
  if (o.status === 'DEPOSIT_PAID') return ['CALL_TECHNICIAN'];
  if (o.status === 'INSTALLING') return ['ACCEPT', 'CALL_TECHNICIAN'];
  if (o.status === 'PENDING_BALANCE') return ['PAY_BALANCE', 'CALL_TECHNICIAN', 'REPAIR'];
  if (o.status === 'COMPLETED') {
    const acts = ['REPAIR'];
    if (!EVALUATED[o.id]) acts.unshift('EVALUATE');
    return acts;
  }
  return [];
}

function workStatusText(w) {
  if (w.status === 'CLOSED') return '已关闭';
  if (w.type === 'REPAIR' && w.quoteAmount > 0 && !w.quoteConfirmed && w.status !== 'DONE') return '待确认报价';
  if (w.status === 'PENDING') return w.type === 'INSTALL' ? '待接单' : '待派单';
  if (w.status === 'ON_SITE') return '服务中';
  return STATUS_TEXT[w.status] || w.status;
}

function decorateWork(w) {
  return Object.assign({}, w, {
    typeText: w.type === 'INSTALL' ? '安装' : '维修',
    statusText: workStatusText(w),
    faultText: FAULT_TEXT[w.faultType] || w.faultText || '其他'
  });
}

function addrLine(a) {
  if (!a) return '';
  return (a.province || '') + (a.city || '') + (a.district || '') + (a.detail || '');
}

function kmBetween(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const toRad = (n) => n * Math.PI / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function spawnInstall(o) {
  seq.work += 1;
  const id = 660000 + seq.work;
  const item = (o.items && o.items[0]) || { name: '新机安装' };
  WORKORDERS[id] = {
    id,
    workNo: 'WO20251123' + String(seq.work).padStart(4, '0'),
    type: 'INSTALL',
    status: 'PENDING',
    faultType: 'INSTALL',
    faultDesc: item.name + ' · ' + (item.skuName || ''),
    inWarranty: true,
    warrantyDesc: '新机安装，验收后起算保修',
    quoteAmount: 0,
    quoteConfirmed: true,
    actualAmount: 0,
    appointmentAt: (o.installDate || '') + (o.installSlot === 'PM' ? ' 14:00:00' : ' 09:30:00'),
    createdAt: now(),
    customerName: (o.address && o.address.name) || '张建国',
    address: addrLine(o.address),
    distanceKm: 2.4,
    technician: o.technician,
    images: [],
    orderId: o.id,
    assetModel: item.name,
    latitude: CUSTOMER_GEO.latitude,
    longitude: CUSTOMER_GEO.longitude,
    timeline: [
      { status: 'PENDING', title: '待接单', desc: '定金已付，等待师傅确认上门时间', time: now(), done: true, current: true },
      { status: 'ACCEPTED', title: '已接单', desc: '', time: '', done: false, current: false },
      { status: 'ON_SITE', title: '已到场', desc: '', time: '', done: false, current: false },
      { status: 'DONE', title: '安装完成', desc: '', time: '', done: false, current: false }
    ]
  };
  o.workOrderId = id;
  return WORKORDERS[id];
}

function pushTimeline(w, status, title, desc) {
  const list = w.timeline || [];
  list.forEach((n) => { n.current = false; });
  const hit = list.filter((n) => n.status === status)[0];
  if (hit) {
    hit.done = true;
    hit.current = true;
    hit.title = title;
    hit.desc = desc;
    hit.time = now();
  } else {
    list.push({ status, title, desc, time: now(), done: true, current: true });
  }
  w.timeline = list;
}

/* ----------------------------- 路由 ----------------------------- */
const ROUTES = [
  ['POST', /^\/api\/auth\/login$/, () => ok({
    token: 'mock-token', expiresIn: 2592000, hasMobile: phoneBound,
    customer: { id: 1001, nickname: '张建国', avatar: '', mobile: phoneBound ? '138****8000' : '', level: 3, levelText: '金卡客户' }
  })],

  ['POST', /^\/api\/auth\/phone$/, () => {
    phoneBound = true;
    return ok({ mobile: '138****8000' });
  }],

  ['GET', /^\/api\/home$/, () => ok({
    banners: [{
      id: 1, image: '', title: '新机到家，师傅上门',
      subtitle: '下单可选安装师傅 · 安装验收后再付尾款',
      linkType: 'PRODUCT_LIST', linkValue: '0'
    }],
    quickEntries: [
      { code: 'BUY', title: '买机器', icon: '机', badge: '' },
      { code: 'REPAIR', title: '一键报修', icon: '修', badge: '' },
      { code: 'ASSET', title: '我的设备', icon: '档', badge: String(ASSETS.length) },
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
        depositAmount: Math.floor(p.price * p.depositRate / 100), stock: p.stock, tags: p.tags, brand: p.brand
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
    if (q && q.keyword) {
      const kw = String(q.keyword).toLowerCase();
      list = list.filter((p) => p.name.toLowerCase().indexOf(kw) >= 0 || p.model.toLowerCase().indexOf(kw) >= 0);
    }
    list = list.map((p) => ({
      id: p.id, name: p.name, model: p.model, coverImage: '', price: p.price, brand: p.brand,
      depositAmount: Math.floor(p.price * p.depositRate / 100), stock: p.stock, tags: p.tags
    }));
    if (q && q.sort === 'price_asc') list.sort((a, b) => a.price - b.price);
    else if (q && q.sort === 'price_desc') list.sort((a, b) => b.price - a.price);
    return ok({ total: list.length, page: Number((q && q.page) || 1), size: 20, list });
  }],

  ['GET', /^\/api\/products\/(\d+)$/, (m, path, q, id) => {
    const p = PRODUCTS[id];
    if (!p) return fail(1001, '机型不存在');
    const data = Object.assign({}, p, {
      depositAmount: Math.floor(p.price * p.depositRate / 100),
      images: [],
      videoUrl: ''
    });
    data.skus = p.skus.map((s) => Object.assign({}, s, {
      depositAmount: Math.floor(s.price * p.depositRate / 100)
    }));
    return ok(data);
  }],

  ['GET', /^\/api\/technicians\/available$/, (m, path, q) => {
    let list = TECHS.map((t) => Object.assign({}, t, { freeSlots: t.freeSlots.slice(), slots: t.slots.slice() }));
    const sort = (q && q.sort) || 'recommend';
    if (sort === 'distance') list.sort((a, b) => a.distanceKm - b.distanceKm);
    else if (sort === 'rating') list.sort((a, b) => b.rating - a.rating);
    else list.sort((a, b) => (b.recommended === true) - (a.recommended === true) || a.distanceKm - b.distanceKm);
    if (q && q.date) {
      list = list.map((t) => {
        const hit = (t.freeSlots || []).some((s) => s.date === q.date);
        return Object.assign({}, t, { bookable: hit });
      });
    }
    return ok(list);
  }],

  ['GET', /^\/api\/orders$/, (m, path, q) => {
    let list = Object.keys(ORDERS).map((k) => ORDERS[k]);
    if (q && q.status && q.status !== 'ALL') {
      if (q.status === 'AFTER_SALE') list = [];
      else list = list.filter((o) => o.status === q.status);
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
    if (found.sku.stock <= 0) return fail(3001, '该配置暂时缺货');
    const qty = Number(body.quantity || 1);
    const total = found.sku.price * qty;
    const deposit = Math.floor(total * found.product.depositRate / 100);
    let tech = null;
    if (body.technicianId) {
      tech = TECHS.filter((t) => t.id === Number(body.technicianId))[0];
      if (!tech) return fail(3002, '师傅档期已满，请重新选择');
      if (body.installDate) {
        const okSlot = (tech.freeSlots || []).some((s) => s.date === body.installDate && (!body.installSlot || s.slot === body.installSlot));
        if (!okSlot) return fail(3002, '师傅 ' + body.installDate + ' 该时段档期已满');
      }
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
      technician: tech ? { id: tech.id, name: tech.name, mobile: '13812348000', rating: tech.rating } : null,
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
    d.workOrderId = o.workOrderId || 0;
    d.reworkRemark = o.reworkRemark || '';
    return ok(d);
  }],

  ['POST', /^\/api\/orders\/(\d+)\/cancel$/, (m, path, body, id) => {
    const o = ORDERS[id];
    if (!o) return fail(1001, '订单不存在');
    if (o.status !== 'UNPAID') return fail(3003, '当前状态不允许取消，已付定金请联系店长审批退款');
    o.status = 'CANCELLED';
    o.times.CANCELLED = now();
    return ok(true);
  }],

  ['POST', /^\/api\/orders\/(\d+)\/accept$/, (m, path, body, id) => {
    const o = ORDERS[id];
    if (!o) return fail(1001, '订单不存在');
    if (body.pass === false) {
      o.status = 'INSTALLING';
      o.reworkRemark = body.remark || '验收不通过';
      o.times.INSTALLING = now();
      return ok({ status: 'INSTALLING', balanceAmount: o.balanceAmount });
    }
    o.status = 'PENDING_BALANCE';
    o.times.PENDING_BALANCE = now();
    return ok({ status: o.status, balanceAmount: o.balanceAmount });
  }],

  ['POST', /^\/api\/pay\/prepay$/, (m, path, body) => {
    if (body.type === 'REPAIR') {
      const w = WORKORDERS[body.orderId];
      if (!w) return fail(1001, '工单不存在');
      const amount = w.actualAmount || w.quoteAmount || 0;
      return ok({
        paymentId: 700 + Number(body.orderId),
        outTradeNo: w.workNo + '-R',
        amount,
        payParams: { timeStamp: '' + Date.now(), nonceStr: 'mock', package: 'prepay_id=mock', signType: 'RSA', paySign: 'mock' }
      });
    }
    const o = ORDERS[body.orderId];
    if (!o) return fail(1001, '订单不存在');
    const isDeposit = body.type === 'DEPOSIT';
    if (!isDeposit && o.status !== 'PENDING_BALANCE' && o.status !== 'COMPLETED') {
      /* 允许重复拉起，金额仍由服务端给 */
    }
    return ok({
      paymentId: 501,
      outTradeNo: o.orderNo + (isDeposit ? '-D' : '-B'),
      amount: isDeposit ? o.depositAmount : o.balanceAmount,
      payParams: { timeStamp: '' + Date.now(), nonceStr: 'mock', package: 'prepay_id=mock', signType: 'RSA', paySign: 'mock' }
    });
  }],

  ['POST', /^\/api\/pay\/mock-success$/, (m, path, body) => {
    if (body.type === 'REPAIR') {
      const w = WORKORDERS[body.orderId];
      if (!w) return fail(1001, '工单不存在');
      w.paid = true;
      w.actualAmount = w.quoteAmount;
      return ok({ status: w.status, paid: true });
    }
    const o = ORDERS[body.orderId];
    if (!o) return fail(1001, '订单不存在');
    if (body.type === 'DEPOSIT') {
      if (o.status === 'UNPAID') {
        o.status = 'DEPOSIT_PAID';
        o.paidAmount = o.depositAmount;
        o.balanceAmount = o.totalAmount - o.paidAmount;
        o.times.DEPOSIT_PAID = now();
        o.payments.push({ id: 501, type: 'DEPOSIT', typeText: '定金', amount: o.depositAmount, status: 'SUCCESS', paidAt: now() });
        if (!o.workOrderId) spawnInstall(o);
      }
    } else if (o.status !== 'COMPLETED') {
      const amt = o.balanceAmount;
      o.paidAmount = o.totalAmount;
      o.balanceAmount = 0;
      o.status = 'COMPLETED';
      o.times.COMPLETED = now();
      o.payments.push({ id: 502, type: 'BALANCE', typeText: '尾款', amount: amt, status: 'SUCCESS', paidAt: now() });
    }
    return ok({ status: o.status });
  }],

  ['GET', /^\/api\/customer\/profile$/, () => {
    const pending = Object.keys(ORDERS).map((k) => ORDERS[k]).reduce((s, o) => s + (o.balanceAmount || 0), 0);
    return ok({
      customer: {
        id: 1001, nickname: '张建国', avatar: '',
        mobile: '138****8000', level: 3, levelText: '金卡客户'
      },
      orderCount: Object.keys(ORDERS).length,
      totalPaid: 4260000,
      pendingAmount: pending,
      assetCount: ASSETS.length,
      couponCount: 2
    });
  }],

  ['GET', /^\/api\/customer\/addresses$/, () => ok(ADDRESSES)],

  ['POST', /^\/api\/customer\/addresses$/, (m, path, body) => {
    seq.addr += 1;
    const row = {
      id: seq.addr,
      name: body.name || '',
      mobile: body.mobile || '',
      province: body.province || '',
      city: body.city || '',
      district: body.district || '',
      detail: body.detail || '',
      isDefault: !!body.isDefault
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
    } else if (body.isDefault === false) row.isDefault = false;
    return ok(true);
  }],

  ['DELETE', /^\/api\/customer\/addresses\/(\d+)$/, (m, path, body, id) => {
    const idx = ADDRESSES.findIndex((a) => a.id === Number(id));
    if (idx < 0) return fail(1001, '地址不存在');
    const wasDefault = ADDRESSES[idx].isDefault;
    ADDRESSES.splice(idx, 1);
    if (wasDefault && ADDRESSES[0]) ADDRESSES[0].isDefault = true;
    return ok(true);
  }],

  ['GET', /^\/api\/assets$/, () => ok(ASSETS)],

  ['POST', /^\/api\/assets\/bind$/, (m, path, body) => {
    const sn = (body.sn || '').trim();
    if (!sn) return fail(1001, '请填写机身码');
    const hit = ASSETS.filter((a) => a.sn === sn)[0];
    if (hit) return ok(hit);
    return fail(1001, '未查询到该机身码，请联系经销商');
  }],

  ['GET', /^\/api\/repairs$/, () => {
    const list = Object.keys(WORKORDERS).map((k) => WORKORDERS[k]).filter((w) => w.type === 'REPAIR');
    list.sort((a, b) => b.id - a.id);
    return ok({ total: list.length, page: 1, size: 20, list: list.map(decorateWork) });
  }],

  ['POST', /^\/api\/repairs$/, (m, path, body) => {
    seq.work += 1;
    const id = 660000 + seq.work;
    const asset = ASSETS.filter((a) => a.id === Number(body.assetId))[0] || ASSETS[0];
    const inW = asset.warrantyStatus === 'IN';
    let tech = null;
    if (body.technicianId) tech = TECHS.filter((t) => t.id === Number(body.technicianId))[0] || null;
    const w = baseRepair({
      id,
      workNo: 'WO20251123' + String(seq.work).padStart(4, '0'),
      status: 'PENDING',
      faultType: body.faultType,
      faultDesc: body.faultDesc,
      inWarranty: inW,
      warrantyDesc: inW ? ('整机保修至 ' + asset.warrantyEnd + '，本次故障在保修范围内') : '已过保，需先检测报价',
      quoteAmount: 0,
      quoteConfirmed: inW,
      assetId: asset.id,
      assetModel: asset.model,
      appointmentAt: body.expectDate ? (body.expectDate + (body.expectSlot === 'PM' ? ' 14:00:00' : ' 09:30:00')) : '',
      createdAt: now(),
      technician: tech ? { id: tech.id, name: tech.name, mobile: '13812348000', rating: tech.rating } : null,
      images: body.images || [],
      video: body.video || '',
      timeline: [
        { status: 'PENDING', title: '提交报修', desc: inW ? '已判定保内，等待派单' : '已判定保外，等待检测报价', time: now(), done: true, current: true },
        { status: 'ACCEPTED', title: '师傅接单', desc: '', time: '', done: false, current: false },
        { status: 'DONE', title: '维修完成', desc: '', time: '', done: false, current: false }
      ]
    });
    WORKORDERS[id] = w;
    return ok({
      workOrderId: id, workNo: w.workNo, inWarranty: inW,
      warrantyDesc: w.warrantyDesc, status: 'PENDING', statusText: '待派单'
    });
  }],

  ['GET', /^\/api\/repairs\/(\d+)$/, (m, path, q, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    return ok(decorateWork(w));
  }],

  ['POST', /^\/api\/repairs\/(\d+)\/quote\/confirm$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    if (body.accept === false) {
      w.status = 'CLOSED';
      w.quoteConfirmed = false;
      pushTimeline(w, 'CLOSED', '已拒绝报价', body.remark || '客户拒绝报价，工单关闭');
      return ok({ status: 'CLOSED' });
    }
    w.quoteConfirmed = true;
    w.status = 'ACCEPTED';
    w.actualAmount = w.quoteAmount;
    pushTimeline(w, 'ACCEPTED', '已确认报价', '客户同意报价，可以施工');
    return ok({ status: 'ACCEPTED' });
  }],

  ['POST', /^\/api\/evaluations$/, (m, path, body) => {
    if (!body || !body.score) return fail(1001, '请先打分');
    if (body.orderId) EVALUATED[body.orderId] = body;
    if (body.workOrderId) EVALUATED['w' + body.workOrderId] = body;
    return ok(true);
  }],

  ['GET', /^\/api\/upload\/policy$/, () => ok({
    host: '', dir: 'mock/', expireAt: 0, policy: 'mock', signature: 'mock', accessKeyId: 'mock'
  })],

  ['GET', /^\/api\/tech\/workorders$/, (m, path, q) => {
    let list = Object.keys(WORKORDERS).map((k) => WORKORDERS[k]);
    if (q && q.status) list = list.filter((w) => w.status === q.status);
    list.sort((a, b) => b.id - a.id);
    return ok({ total: list.length, page: 1, size: 20, list: list.map(decorateWork) });
  }],

  /* 契约未单列，响应体与列表项相同，供工单详情页使用 */
  ['GET', /^\/api\/tech\/workorders\/(\d+)$/, (m, path, q, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    return ok(decorateWork(w));
  }],

  ['POST', /^\/api\/tech\/workorders\/(\d+)\/accept$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    if (w.status !== 'PENDING' && w.status !== 'ACCEPTED') return fail(3003, '当前状态不能接单');
    w.status = 'ACCEPTED';
    if (body && body.appointmentAt) w.appointmentAt = body.appointmentAt;
    pushTimeline(w, 'ACCEPTED', '已接单', w.appointmentAt ? ('约定 ' + w.appointmentAt) : '已确认上门');
    return ok({ status: 'ACCEPTED' });
  }],

  /* 契约未定义。Mock 用来演示拒单后工单关闭，真实环境需后端补接口再改派 */
  ['POST', /^\/api\/tech\/workorders\/(\d+)\/reject$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    if (w.status !== 'PENDING') return fail(3003, '只有待接单可以拒绝');
    w.status = 'CLOSED';
    w.rejectReason = (body && body.reason) || '师傅拒单';
    pushTimeline(w, 'CLOSED', '师傅拒单', w.rejectReason);
    return ok({ status: 'CLOSED' });
  }],

  ['POST', /^\/api\/tech\/workorders\/(\d+)\/checkin$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    if (w.status !== 'ACCEPTED' && w.status !== 'ON_SITE') return fail(3003, '请先接单再打卡');
    const lat = Number(body && body.latitude);
    const lng = Number(body && body.longitude);
    if (!lat || !lng) return fail(1001, '缺少定位');
    const km = kmBetween(lat, lng, w.latitude || CUSTOMER_GEO.latitude, w.longitude || CUSTOMER_GEO.longitude);
    if (km > 0.5) return fail(3005, '距离客户地址超过 500 米，无法打卡');
    w.status = 'ON_SITE';
    w.checkinAt = now();
    w.checkinAddress = (body && body.address) || '已到达客户地址附近';
    pushTimeline(w, 'ON_SITE', '已到场打卡', w.checkinAddress);
    return ok({ status: 'ON_SITE', checkinAt: w.checkinAt });
  }],

  ['POST', /^\/api\/tech\/workorders\/(\d+)\/finish$/, (m, path, body, id) => {
    const w = WORKORDERS[id];
    if (!w) return fail(1001, '工单不存在');
    if (w.status !== 'ON_SITE' && w.status !== 'ACCEPTED') return fail(3003, '请先到场再提交完工');
    if (w.type === 'REPAIR' && !w.inWarranty && !w.quoteConfirmed) return fail(3003, '保外报价尚未确认，不能施工');
    const images = (body && body.images) || [];
    if (w.type === 'INSTALL') {
      if (!body || !body.sn) return fail(1001, '请填写或扫描机身码');
      if (images.length < 3) return fail(1001, '安装单至少上传 3 张现场照片');
      if (!body.signImage) return fail(1001, '请让客户签字');
      w.sn = body.sn;
    } else if (!body || !body.remark) {
      return fail(1001, '请填写处理措施');
    }
    w.images = images;
    w.signImage = (body && body.signImage) || '';
    w.parts = (body && body.parts) || [];
    w.oldPartHandling = (body && body.oldPartHandling) || '';
    w.remark = (body && body.remark) || '';
    w.status = 'DONE';
    w.finishAt = now();
    pushTimeline(w, 'DONE', w.type === 'INSTALL' ? '安装完成' : '维修完成', '已提交完工');
    if (w.type === 'INSTALL' && w.orderId && ORDERS[w.orderId]) {
      const o = ORDERS[w.orderId];
      if (o.status === 'DEPOSIT_PAID' || o.status === 'INSTALLING') {
        o.status = 'INSTALLING';
        o.times.INSTALLING = now();
      }
    }
    return ok({ status: 'DONE', finishAt: w.finishAt });
  }]
];

/** 供 request.js 调用，返回值与 wx.request 的 result 对齐 */
async function request(path, method, data) {
  await delay(60 + Math.random() * 40);
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
