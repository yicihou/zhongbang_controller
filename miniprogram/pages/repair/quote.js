const api = require('../../utils/api');
const fmt = require('../../utils/format');

Page({
  data: {
    w: {},
    items: [],
    remark: '',
    must: false,
    submitting: false
  },

  onLoad(q) {
    this.id = q.id;
    api.getRepair(this.id).then((w) => {
      this.setData({
        w: w,
        must: (w.quoteAmount || 0) > 30000,
        items: (w.quoteItems || []).map((it) => Object.assign({}, it, { amountText: fmt.yuan(it.amount) }))
      });
    });
  },

  onRemark(e) {
    this.setData({ remark: e.detail.value });
  },

  decide(e) {
    if (this.data.submitting) return;
    const accept = e.currentTarget.dataset.accept === '1';
    if (!accept && !this.data.remark.trim()) {
      wx.showToast({ title: '拒绝时请写明原因', icon: 'none' });
      return;
    }
    this.setData({ submitting: true });
    api.confirmQuote(this.id, accept, this.data.remark).then(() => {
      wx.showToast({ title: accept ? '已确认，师傅可以施工' : '已拒绝，工单关闭', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 500);
    }).catch(() => this.setData({ submitting: false }));
  }
});
