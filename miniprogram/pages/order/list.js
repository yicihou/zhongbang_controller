const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { pay } = require('../../utils/pay');

const TABS = [
  { label: '全部', value: 'ALL' },
  { label: '待付款', value: 'UNPAID' },
  { label: '待安装', value: 'DEPOSIT_PAID' },
  { label: '待验收', value: 'INSTALLING' },
  { label: '待尾款', value: 'PENDING_BALANCE' },
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
          totalText: fmt.yuan(o.totalAmount),
          paidText: fmt.yuan(o.paidAmount),
          balanceText: fmt.yuan(o.balanceAmount),
          depositText: fmt.yuan(o.depositAmount),
          metaText: (o.installDate ? '安装 ' + o.installDate : '') + (o.technicianName ? ' · ' + o.technicianName : ''),
          items: (o.items || []).map((it) => Object.assign({}, it, { priceText: fmt.yuan(it.price) }))
        })),
        repairs: (r.list || []).map((w) => Object.assign({}, w, {
          techName: (w.technician && w.technician.name) || '待派师傅',
          quoteText: fmt.yuan(w.quoteAmount || 0),
          timeline: (w.timeline || []).slice(0, 4)
        }))
      });
    }).catch(() => this.setData({ loading: false }));
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/order/detail?id=' + e.currentTarget.dataset.id });
  },

  goRepair(e) {
    wx.navigateTo({ url: '/pages/repair/detail?id=' + e.currentTarget.dataset.id });
  },

  pay(e) {
    const id = e.currentTarget.dataset.id;
    const type = e.currentTarget.dataset.type;
    pay(id, type).then(() => {
      wx.navigateTo({ url: '/pages/order/result?orderId=' + id + '&type=' + type });
    }).catch((err) => {
      if (err && err.msg && !err.code) wx.showToast({ title: err.msg, icon: 'none' });
    });
  }
});
