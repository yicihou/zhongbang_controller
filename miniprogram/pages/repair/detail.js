const api = require('../../utils/api');
const fmt = require('../../utils/format');
const refresh = require('../../utils/refresh');
const { payOfflineIntent } = require('../../utils/pay');
const { FAULTS, PAYMENT_TYPE_TEXT, PAYMENT_STATUS_TEXT } = require('../../utils/constants');

const FAULT_MAP = {};
FAULTS.forEach((f) => { FAULT_MAP[f.code] = f.label; });

Page({
  data: {
    w: { timeline: [], quoteItems: [], lines: [], payments: [] },
    contact: null,
    needConfirm: false,
    canOffline: false,
    intenting: false
  },

  onLoad(q) {
    this.id = q.id;
  },

  onShow() {
    this.load();
    refresh.start(this, { areas: ['repairs'], load: () => this.load() });
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
      api.getRepair(this.id),
      api.getAdminContact().catch(() => null)
    ]).then(([w, contact]) => {
      const items = (w.quoteItems || []).map((it) => Object.assign({}, it, { amountText: fmt.yuan(it.amount) }));
      const lines = (w.lines || []).map((L) => Object.assign({}, L, {
        faultText: FAULT_MAP[L.faultType] || L.faultType,
        sourceText: L.source === 'EXTERNAL' ? '外购' : '本店',
        quoteText: fmt.yuan(L.lineQuoteAmount || 0)
      }));
      const payments = (w.payments || []).map((p) => Object.assign({}, p, {
        amountText: fmt.yuan(p.amount),
        typeText: p.typeText || PAYMENT_TYPE_TEXT[p.type] || p.type,
        statusText: p.statusText || PAYMENT_STATUS_TEXT[p.status] || p.status
      }));
      const needConfirm = w.status === 'QUOTED' && (w.quoteAmount || 0) > 0;
      const canOffline = w.status === 'PENDING_PAYMENT' || payments.some((p) => p.status === 'WAIT');
      this.setData({
        w: Object.assign({}, w, {
          quoteItems: items,
          lines,
          payments,
          quoteText: fmt.yuan(w.quoteAmount || 0),
          workNo: w.ticketNo || w.workNo
        }),
        contact,
        needConfirm,
        canOffline
      });
    });
  },

  goQuote() {
    wx.navigateTo({ url: '/pages/repair/quote?id=' + this.id });
  },

  offlineIntent() {
    if (this.data.intenting) return;
    this.setData({ intenting: true });
    payOfflineIntent('REPAIR', this.id, null, '维修费将线下转账').then((res) => {
      this.setData({ intenting: false });
      wx.showToast({ title: (res && res.tip) || '已通知管理员', icon: 'none' });
      this.load();
    }).catch(() => this.setData({ intenting: false }));
  },

  copyWechat() {
    const w = this.data.contact && this.data.contact.wechat;
    if (w) wx.setClipboardData({ data: String(w) });
  },

  callAdmin() {
    const p = this.data.contact && this.data.contact.phone;
    if (p) wx.makePhoneCall({ phoneNumber: String(p) });
  },

  goEvaluate() {
    if (this.data.w.status !== 'DONE') {
      wx.showToast({ title: '维修完成后再评价', icon: 'none' });
      return;
    }
    wx.navigateTo({
      url: '/pages/order/evaluate?workOrderId=' + this.id + '&title=' + encodeURIComponent(this.data.w.faultText || '维修')
    });
  }
});
