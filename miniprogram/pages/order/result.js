const api = require('../../utils/api');
const fmt = require('../../utils/format');
const config = require('../../config');

Page({
  data: {
    orderId: 0,
    type: 'DEPOSIT',
    typeText: '定金',
    amountText: '0',
    amountCents: 0,
    orderNo: '',
    nextText: '',
    loading: true
  },

  onLoad(q) {
    this.setData({
      orderId: Number(q.orderId || q.id || 0),
      type: q.type || 'DEPOSIT'
    });
    this.load();
  },

  load() {
    const type = this.data.type;
    if (type === 'REPAIR') {
      return api.getRepair(this.data.orderId).then((w) => {
        this.setData({
          loading: false,
          typeText: '维修费',
          amountCents: w.actualAmount || w.quoteAmount || 0,
          amountText: fmt.yuan(w.actualAmount || w.quoteAmount),
          orderNo: w.workNo,
          nextText: '支付结果以微信支付回调为准。可在报修详情查看进度。'
        });
      }).catch(() => this.setData({ loading: false }));
    }
    return api.getOrder(this.data.orderId).then((o) => {
      const isDeposit = type !== 'BALANCE';
      let amount = isDeposit ? o.depositAmount : 0;
      if (!isDeposit) {
        const hit = (o.payments || []).filter((p) => p.type === 'BALANCE');
        amount = hit.length ? hit[hit.length - 1].amount : o.totalAmount - o.depositAmount;
      }
      this.setData({
        loading: false,
        typeText: isDeposit ? '定金' : '尾款',
        amountCents: amount,
        amountText: fmt.yuan(amount),
        orderNo: o.orderNo,
        nextText: isDeposit
          ? '师傅会按预约时间上门。安装完成、您确认验收之后，再支付尾款。'
          : '尾款已结清，订单完成。欢迎对本次安装进行评价。'
      });
    }).catch(() => this.setData({ loading: false }));
  },

  goDetail() {
    const url = this.data.type === 'REPAIR'
      ? '/pages/repair/detail?id=' + this.data.orderId
      : '/pages/order/detail?id=' + this.data.orderId;
    wx.redirectTo({ url });
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' });
  },

  subscribe() {
    const ids = config.subscribeTmplIds || [];
    if (!ids.length || !wx.requestSubscribeMessage) {
      wx.showToast({ title: '进度提醒模板尚未配置', icon: 'none' });
      return;
    }
    wx.requestSubscribeMessage({ tmplIds: ids });
  }
});
