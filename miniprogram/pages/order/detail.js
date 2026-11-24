const api = require('../../utils/api');
const fmt = require('../../utils/format');
const refresh = require('../../utils/refresh');
const { payOfflineIntent } = require('../../utils/pay');
const { deliveryLabel, PAYMENT_TYPE_TEXT, PAYMENT_STATUS_TEXT } = require('../../utils/constants');

const DESC = {
  PENDING_PAYMENT: '请联系管理员线下转账；管理员可改价、拆定金/尾款或加运费',
  PARTIALLY_PAID: '部分款项已入账，请继续完成剩余待付款',
  PAID: '款项已结清，等待发货或上门安装',
  FULFILLING: '正在发货 / 安装履约中',
  COMPLETED: '订单已完成，感谢使用',
  CANCELLED: '订单已取消'
};

Page({
  data: {
    o: { address: {}, items: [], timeline: [], payments: [], waitPayments: [] },
    shipments: [],
    contact: null,
    statusDesc: '',
    hasAction: false,
    canCancel: false,
    canOffline: false,
    canRepair: false,
    canEvaluate: false,
    waitTotalText: '0',
    intenting: false
  },

  onLoad(options) {
    this.id = options.id;
    if (options.created) {
      wx.showToast({ title: '订单已提交', icon: 'success' });
    }
  },

  onShow() {
    this.load();
    refresh.start(this, { areas: ['orders'], load: () => this.load() });
  },

  onHide() {
    refresh.stop(this);
  },

  onUnload() {
    refresh.stop(this);
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    return Promise.all([
      api.getOrder(this.id),
      api.getShipments(this.id).catch(() => []),
      api.getAdminContact().catch(() => null)
    ]).then(([o, shipments, contact]) => {
      const wait = o.waitPayments || (o.payments || []).filter((p) => p.status === 'WAIT');
      const actions = o.actions || [];
      const shipmentsView = (shipments || []).map((s) => this.formatShipment(s));
      this.setData({
        o: Object.assign({}, o, {
          goodsText: fmt.yuan(o.goodsAmount || o.totalAmount || 0),
          payableText: fmt.yuan(o.payableAmount || o.totalAmount || 0),
          paidText: fmt.yuan(o.paidAmount || 0),
          freightText: fmt.yuan(o.freightAmount || 0),
          installText: fmt.yuan(o.installAmount || 0),
          adjustText: fmt.yuan(Math.abs(o.adjustAmount || 0)),
          adjustSign: (o.adjustAmount || 0) < 0 ? '-' : '+',
          items: (o.items || []).map((it) => Object.assign({}, it, {
            priceText: fmt.yuan(it.unitPrice || it.price || 0),
            lineText: fmt.yuan(it.lineAmount || (it.price * it.quantity) || 0),
            deliveryMethodText: it.deliveryMethodText || deliveryLabel(it.deliveryMethod),
            installFeeTip: it.installFeeText || (it.deliveryMethod === 'ON_SITE_INSTALL' ? '待管理员确认' : '')
          })),
          payments: (o.payments || []).map((p) => Object.assign({}, p, {
            amountText: fmt.yuan(p.amount),
            typeText: p.typeText || PAYMENT_TYPE_TEXT[p.type] || p.type,
            statusText: p.statusText || PAYMENT_STATUS_TEXT[p.status] || p.status
          })),
          waitPayments: wait.map((p) => Object.assign({}, p, {
            amountText: fmt.yuan(p.amount),
            typeText: p.typeText || PAYMENT_TYPE_TEXT[p.type] || p.type
          }))
        }),
        shipments: shipmentsView,
        contact,
        statusDesc: DESC[o.status] || '',
        waitTotalText: fmt.yuan(wait.reduce((s, p) => s + p.amount, 0)),
        canCancel: actions.indexOf('CANCEL') >= 0 || o.status === 'PENDING_PAYMENT',
        canOffline: wait.length > 0,
        canRepair: actions.indexOf('REPAIR') >= 0 || o.status === 'COMPLETED',
        canEvaluate: actions.indexOf('EVALUATE') >= 0,
        hasAction: wait.length > 0 || actions.indexOf('CANCEL') >= 0 || o.status === 'COMPLETED'
      });
    });
  },

  formatShipment(s) {
    const method = s.method || s.deliveryMethod;
    const base = {
      id: s.id,
      method,
      methodText: deliveryLabel(method),
      status: s.status,
      statusText: s.status === 'DONE' ? '已完成' : (s.status === 'DISPATCHED' ? '已发出' : '待发货'),
      lines: []
    };
    if (method === 'EXPRESS') {
      base.lines.push({ k: '承运商', v: s.carrier || '-' });
      base.lines.push({ k: '运单号', v: s.trackingNo || '-', copy: s.trackingNo });
    } else if (method === 'FREIGHT') {
      base.lines.push({ k: '物流公司', v: s.logisticsCompany || '-' });
      base.lines.push({ k: '货运单号', v: s.freightNo || '-', copy: s.freightNo });
      if (s.vehicleNo) base.lines.push({ k: '车牌', v: s.vehicleNo });
    } else if (method === 'HOME_DELIVERY' || method === 'ON_SITE_INSTALL') {
      base.lines.push({ k: '送货/师傅', v: s.courierName || '-' });
      base.lines.push({ k: '联系电话', v: s.courierMobile || '-', call: s.courierMobile });
      if (s.appointAt) base.lines.push({ k: '预约时间', v: s.appointAt });
    } else if (method === 'STORE_PICKUP') {
      base.lines.push({ k: '自提码', v: s.pickupCode || '-', copy: s.pickupCode });
      if (s.pickedAt) base.lines.push({ k: '提货时间', v: s.pickedAt });
    }
    if (s.remark) base.lines.push({ k: '备注', v: s.remark });
    return base;
  },

  offlineIntent() {
    if (this.data.intenting || !this.data.canOffline) return;
    this.setData({ intenting: true });
    payOfflineIntent('ORDER', this.id, null, '客户将线下转账').then((res) => {
      this.setData({ intenting: false });
      wx.showToast({ title: (res && res.tip) || '已通知管理员', icon: 'none' });
      this.load();
    }).catch(() => this.setData({ intenting: false }));
  },

  copyWechat() {
    const w = this.data.contact && this.data.contact.wechat;
    if (!w) return;
    wx.setClipboardData({ data: String(w) });
  },

  callAdmin() {
    const p = this.data.contact && this.data.contact.phone;
    if (!p) return;
    wx.makePhoneCall({ phoneNumber: String(p) });
  },

  copyText(e) {
    const v = e.currentTarget.dataset.v;
    if (!v) return;
    wx.setClipboardData({ data: String(v) });
  },

  callPhone(e) {
    const v = e.currentTarget.dataset.v;
    if (!v) return;
    wx.makePhoneCall({ phoneNumber: String(v).replace(/\*/g, '') });
  },

  cancel() {
    wx.showModal({
      title: '取消订单',
      content: '仅未付款订单可取消。确定取消？',
      success: (res) => {
        if (!res.confirm) return;
        api.cancelOrder(this.id, '用户主动取消').then(() => {
          wx.showToast({ title: '已取消', icon: 'success' });
          this.load();
        });
      }
    });
  },

  goRepair() {
    wx.navigateTo({ url: '/pages/repair/create' });
  },

  goEvaluate() {
    const name = (this.data.o.items && this.data.o.items[0] && this.data.o.items[0].name) || '订单服务';
    wx.navigateTo({
      url: '/pages/order/evaluate?orderId=' + this.id + '&title=' + encodeURIComponent(name)
    });
  }
});
