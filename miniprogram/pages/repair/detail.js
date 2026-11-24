const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { pay } = require('../../utils/pay');

Page({
  data: {
    w: { timeline: [], quoteItems: [] },
    needConfirm: false,
    canPay: false,
    techInitial: ''
  },

  onLoad(q) {
    this.id = q.id;
  },

  onShow() {
    this.load();
  },

  load() {
    return api.getRepair(this.id).then((w) => {
      const items = (w.quoteItems || []).map((it) => Object.assign({}, it, { amountText: fmt.yuan(it.amount) }));
      const needConfirm = w.type !== 'INSTALL' && w.quoteAmount > 0 && !w.quoteConfirmed && w.status !== 'CLOSED' && w.status !== 'DONE';
      const canPay = !w.inWarranty && w.quoteConfirmed && !w.paid && w.quoteAmount > 0 && w.status !== 'CLOSED';
      this.setData({
        w: Object.assign({}, w, { quoteItems: items, quoteText: fmt.yuan(w.quoteAmount) }),
        needConfirm,
        canPay,
        techInitial: w.technician ? (w.technician.name || '师').slice(0, 1) : ''
      });
    });
  },

  goQuote() {
    wx.navigateTo({ url: '/pages/repair/quote?id=' + this.id });
  },

  payFee() {
    pay(Number(this.id), 'REPAIR').then(() => {
      wx.navigateTo({ url: '/pages/order/result?orderId=' + this.id + '&type=REPAIR' });
    }).catch((e) => {
      if (e && e.msg && !e.code) wx.showToast({ title: e.msg, icon: 'none' });
    });
  },

  callTech() {
    const t = this.data.w.technician;
    if (!t || !t.mobile) return;
    wx.makePhoneCall({ phoneNumber: t.mobile });
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
