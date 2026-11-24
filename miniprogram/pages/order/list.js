const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { deliveryLabel } = require('../../utils/constants');

const TABS = [
  { label: '全部', value: 'ALL' },
  { label: '待付款', value: 'PENDING_PAYMENT' },
  { label: '已付款', value: 'PAID' },
  { label: '履约中', value: 'FULFILLING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '售后', value: 'AFTER_SALE' }
];

Page({
  data: {
    tabs: TABS,
    status: 'ALL',
    list: [],
    repairs: [],
    loading: true
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  onTab(e) {
    this.setData({ status: e.currentTarget.dataset.value }, () => this.load());
  },

  load() {
    this.setData({ loading: true });
    const status = this.data.status;
    const showRepair = status === 'ALL' || status === 'AFTER_SALE';
    const orders = status === 'AFTER_SALE'
      ? Promise.resolve({ list: [] })
      : api.getOrders({ status, page: 1, size: 20 });
    const repairs = showRepair ? api.listRepairs() : Promise.resolve({ list: [] });
    return Promise.all([orders, repairs]).then(([d, r]) => {
      this.setData({
        loading: false,
        list: (d.list || []).map((o) => Object.assign({}, o, {
          done: o.status === 'COMPLETED',
          totalText: fmt.yuan(o.payableAmount || o.totalAmount || 0),
          paidText: fmt.yuan(o.paidAmount || 0),
          waitText: fmt.yuan(o.waitAmount || 0),
          canOffline: o.status === 'PENDING_PAYMENT' || o.status === 'PARTIALLY_PAID',
          items: (o.items || []).map((it) => Object.assign({}, it, {
            priceText: fmt.yuan(it.unitPrice || it.price || 0),
            deliveryMethodText: it.deliveryMethodText || deliveryLabel(it.deliveryMethod)
          }))
        })),
        repairs: (r.list || []).map((w) => Object.assign({}, w, {
          quoteText: fmt.yuan(w.quoteAmount || 0),
          workNo: w.ticketNo || w.workNo,
          lineHint: (w.lineCount || 1) + ' 台设备'
        }))
      });
    }).catch(() => this.setData({ loading: false }));
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/order/detail?id=' + e.currentTarget.dataset.id });
  },

  goRepair(e) {
    wx.navigateTo({ url: '/pages/repair/detail?id=' + e.currentTarget.dataset.id });
  }
});
