const api = require('../../utils/api');
const fmt = require('../../utils/format');
const config = require('../../config');

const MOCK_TABS = [
  { label: '全部', value: 'ALL' },
  { label: '待付款', value: 'UNPAID' },
  { label: '待安装', value: 'DEPOSIT_PAID' },
  { label: '待付尾款', value: 'PENDING_BALANCE' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '售后', value: 'AFTER_SALE' }
];

const REAL_TABS = [
  { label: '全部', value: 'ALL' },
  { label: '待付款', value: 'PENDING_PAYMENT' },
  { label: '部分付款', value: 'PARTIALLY_PAID' },
  { label: '履约中', value: 'FULFILLING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' }
];

Page({
  data: {
    tabs: config.USE_MOCK ? MOCK_TABS : REAL_TABS,
    status: 'ALL',
    list: [],
    loading: true
  },

  onShow() {
    this.load();
  },

  onTab(e) {
    this.setData({ status: e.currentTarget.dataset.value }, () => this.load());
  },

  load() {
    this.setData({ loading: true });
    return api.getOrders({ status: this.data.status, page: 1, size: 20 }).then((d) => {
      this.setData({
        loading: false,
        list: (d.list || []).map((o) => Object.assign({}, o, {
          done: o.status === 'COMPLETED',
          totalText: fmt.yuan(o.totalAmount),
          paidText: fmt.yuan(o.paidAmount),
          balanceText: fmt.yuan(o.balanceAmount),
          metaText: (o.installDate ? '安装 ' + o.installDate : '') +
            (o.technicianName ? ' · ' + o.technicianName : ''),
          items: (o.items || []).map((it) => Object.assign({}, it, { priceText: fmt.yuan(it.price) }))
        }))
      });
    }).catch(() => this.setData({ loading: false }));
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/order/detail?id=' + e.currentTarget.dataset.id });
  }
});
