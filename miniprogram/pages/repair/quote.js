const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { FAULTS } = require('../../utils/constants');

const FAULT_MAP = {};
FAULTS.forEach((f) => { FAULT_MAP[f.code] = f.label; });

Page({
  data: {
    w: {},
    items: [],
    lines: [],
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
        items: (w.quoteItems || []).map((it) => Object.assign({}, it, { amountText: fmt.yuan(it.amount) })),
        lines: (w.lines || []).map((L) => Object.assign({}, L, {
          faultText: FAULT_MAP[L.faultType] || L.faultType,
          quoteText: fmt.yuan(L.lineQuoteAmount || 0)
        })),
        quoteText: fmt.yuan(w.quoteAmount || 0)
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
      wx.showToast({
        title: accept ? '已确认，请线下转账维修费' : '已拒绝，报修单关闭',
        icon: 'none'
      });
      setTimeout(() => wx.navigateBack(), 500);
    }).catch(() => this.setData({ submitting: false }));
  }
});
