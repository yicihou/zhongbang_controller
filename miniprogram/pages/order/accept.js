const api = require('../../utils/api');
const fmt = require('../../utils/format');

Page({
  data: {
    o: { items: [] },
    remark: '',
    pass: true,
    submitting: false
  },

  onLoad(q) {
    this.id = q.id;
    this.load();
  },

  load() {
    return api.getOrder(this.id).then((o) => {
      this.setData({
        o: Object.assign({}, o, {
          totalText: fmt.yuan(o.totalAmount),
          balanceText: fmt.yuan(o.balanceAmount),
          slotText: fmt.slotText(o.installSlot)
        })
      });
    });
  },

  onRemark(e) {
    this.setData({ remark: e.detail.value });
  },

  choose(e) {
    this.setData({ pass: e.currentTarget.dataset.pass === '1' });
  },

  submit() {
    if (this.data.submitting) return;
    if (!this.data.pass && !this.data.remark.trim()) {
      wx.showToast({ title: '请填写不通过的原因', icon: 'none' });
      return;
    }
    const tip = this.data.pass
      ? '确认机器安装调试正常？确认后会生成尾款。'
      : '将退回师傅返工，尾款不会生成。';
    wx.showModal({
      title: this.data.pass ? '确认验收' : '验收不通过',
      content: tip,
      success: (res) => {
        if (!res.confirm) return;
        this.setData({ submitting: true });
        api.acceptOrder(this.id, this.data.pass, this.data.remark).then(() => {
          wx.showToast({ title: this.data.pass ? '验收成功' : '已转返工', icon: 'none' });
          setTimeout(() => {
            wx.redirectTo({ url: '/pages/order/detail?id=' + this.id });
          }, 400);
        }).catch(() => this.setData({ submitting: false }));
      }
    });
  }
});
