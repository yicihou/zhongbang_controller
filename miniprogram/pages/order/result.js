const api = require('../../utils/api');
const fmt = require('../../utils/format');
const config = require('../../config');

Page({
  data: {
    orderId: 0,
    type: 'OFFLINE',
    title: '已提交线下转账意向',
    amountText: '0',
    amountCents: 0,
    orderNo: '',
    nextText: '',
    loading: true
  },

  onLoad(q) {
    this.setData({
      orderId: Number(q.orderId || q.id || 0),
      type: q.type || 'OFFLINE'
    });
    this.load();
  },

  load() {
    const type = this.data.type;
    if (type === 'REPAIR') {
      return api.getRepair(this.data.orderId).then((w) => {
        this.setData({
          loading: false,
          title: '维修费 · 线下转账',
          amountCents: w.payableAmount || w.quoteAmount || 0,
          amountText: fmt.yuan(w.payableAmount || w.quoteAmount),
          orderNo: w.ticketNo || w.workNo,
          nextText: '请联系管理员转账。入账后由管理员登记，页面状态会更新。'
        });
      }).catch(() => this.setData({ loading: false }));
    }
    return api.getOrder(this.data.orderId).then((o) => {
      const wait = (o.waitPayments || []).reduce((s, p) => s + p.amount, 0);
      this.setData({
        loading: false,
        title: '订单 · 线下转账',
        amountCents: wait || o.payableAmount || 0,
        amountText: fmt.yuan(wait || o.payableAmount || 0),
        orderNo: o.orderNo,
        nextText: '一期不调起微信支付。请按待付款笔次转账，管理员确认后订单变为已付款。'
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
