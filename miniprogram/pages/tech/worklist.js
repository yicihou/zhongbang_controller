const api = require('../../utils/api');

const TABS = [
  { label: '全部', value: '' },
  { label: '待接单', value: 'PENDING' },
  { label: '已接单', value: 'ACCEPTED' },
  { label: '服务中', value: 'ON_SITE' },
  { label: '已完成', value: 'DONE' }
];

Page({
  data: {
    tabs: TABS,
    status: '',
    list: [],
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
    const params = {};
    if (this.data.status) params.status = this.data.status;
    return api.getTechWorkorders(params).then((d) => {
      this.setData({ list: (d && d.list) || [], loading: false });
    }).catch(() => this.setData({ loading: false }));
  },

  open(e) {
    wx.navigateTo({ url: '/pages/tech/detail?id=' + e.currentTarget.dataset.id });
  },

  accept(e) {
    const id = e.currentTarget.dataset.id;
    api.acceptWorkorder(id, {}).then(() => {
      wx.showToast({ title: '已接单，请确认上门时间', icon: 'none' });
      wx.navigateTo({ url: '/pages/tech/detail?id=' + id });
    });
  }
});
