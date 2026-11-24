const api = require('../../utils/api');

Page({
  data: { list: [], loading: true },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    this.setData({ loading: true });
    return api.getAssets().then((list) => {
      this.setData({ list: list || [], loading: false });
    }).catch(() => this.setData({ loading: false }));
  },

  open(e) {
    wx.navigateTo({ url: '/pages/asset/warranty?id=' + e.currentTarget.dataset.id });
  },

  repair(e) {
    wx.navigateTo({ url: '/pages/repair/create?assetId=' + e.currentTarget.dataset.id });
  },

  bindScan() {
    wx.scanCode({
      onlyFromCamera: false,
      success: (res) => this.bindSn(res.result),
      fail: () => {
        wx.showModal({
          title: '输入机身码',
          editable: true,
          placeholderText: '例如 A5-2024-008671',
          success: (r) => {
            if (r.confirm && r.content) this.bindSn(r.content.trim());
          }
        });
      }
    });
  },

  bindSn(sn) {
    api.bindAsset(sn).then((a) => {
      wx.showToast({ title: '已绑定 ' + (a.model || a.sn), icon: 'none' });
      this.load();
    });
  }
});
