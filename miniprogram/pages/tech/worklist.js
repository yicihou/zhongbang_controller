const api = require('../../utils/api');

Page({
  data: {
    status: '',
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
    const params = {};
    if (this.data.status) params.status = this.data.status;
    return api.getTechWorkorders(params).then((d) => {
      this.setData({ list: (d && d.list) || [], loading: false });
    }).catch(() => this.setData({ loading: false }));
  },

  accept(e) {
    const id = e.currentTarget.dataset.id;
    api.acceptWorkorder(id).then(() => {
      wx.showToast({ title: '接单成功', icon: 'success' });
      this.load();
    });
  }
});
