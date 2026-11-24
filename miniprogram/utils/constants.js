/** 与 docs/04 §2.1 枚举对齐；以 03/04 为准 */

const DeliveryMethod = {
  EXPRESS: 'EXPRESS',
  FREIGHT: 'FREIGHT',
  HOME_DELIVERY: 'HOME_DELIVERY',
  ON_SITE_INSTALL: 'ON_SITE_INSTALL',
  STORE_PICKUP: 'STORE_PICKUP'
};

const DELIVERY_OPTIONS = [
  { value: DeliveryMethod.EXPRESS, label: '快递', tip: '承运商 + 运单号' },
  { value: DeliveryMethod.FREIGHT, label: '物流货运', tip: '大件专线' },
  { value: DeliveryMethod.HOME_DELIVERY, label: '上门送货', tip: '店内配送，不含安装调试' },
  { value: DeliveryMethod.ON_SITE_INSTALL, label: '上门安装', tip: '送装一体，安装费待管理员确认' },
  { value: DeliveryMethod.STORE_PICKUP, label: '到店自提', tip: '提货码 + 门店地址' }
];

const ORDER_STATUS_TEXT = {
  PENDING_PAYMENT: '待付款',
  PARTIALLY_PAID: '部分付款',
  PAID: '已付款',
  FULFILLING: '履约中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
};

const REPAIR_STATUS_TEXT = {
  SUBMITTED: '已提交',
  QUOTED: '已报价',
  PENDING_PAYMENT: '待付款',
  PAID: '已付款',
  CONFIRMED: '待派工',
  IN_SERVICE: '维修中',
  DONE: '已完成',
  CANCELLED: '已关闭'
};

const PAYMENT_TYPE_TEXT = {
  FULL: '全款',
  DEPOSIT: '定金',
  BALANCE: '尾款',
  REPAIR: '维修费',
  FREIGHT: '运费'
};

const PAYMENT_STATUS_TEXT = {
  WAIT: '待付款',
  SUCCESS: '已入账',
  CLOSED: '已关闭',
  REFUNDED: '已退款'
};

const FAULTS = [
  { code: 'THREAD_BREAK', label: '断线 / 跳针' },
  { code: 'NOISE', label: '噪音异响' },
  { code: 'OIL', label: '不上油' },
  { code: 'ELECTRIC', label: '电控故障' },
  { code: 'FEED', label: '送布异常' },
  { code: 'OTHER', label: '其他' }
];

function deliveryLabel(method) {
  const hit = DELIVERY_OPTIONS.filter((o) => o.value === method)[0];
  return hit ? hit.label : (method || '');
}

function needInstall(method) {
  return method === DeliveryMethod.ON_SITE_INSTALL;
}

module.exports = {
  DeliveryMethod,
  DELIVERY_OPTIONS,
  ORDER_STATUS_TEXT,
  REPAIR_STATUS_TEXT,
  PAYMENT_TYPE_TEXT,
  PAYMENT_STATUS_TEXT,
  FAULTS,
  deliveryLabel,
  needInstall
};
