const api = require('../../utils/api');
const fmt = require('../../utils/format');
const config = require('../../config');

Page({
  data: {
    profile: { customer: {} },
    assets: [],
    pendingText: '0',
    initial: '微',
    version: config.version,
    mock: config.USE_MOCK
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    const a = api.getProfile().then((p) => {
      const name = (p.customer && p.customer.nickname) || '微';
      this.setData({
        profile: p,
        pendingText: fmt.yuan(p.pendingAmount),
        initial: name.slice(0, 1)
      });
    });
    const b = api.getAssets().then((list) => this.setData({ assets: (list || []).slice(0, 2) }));
    return Promise.all([a, b]);
  },

  go(e) {
    const url = e.currentTarget.dataset.url;
    if (!url) return;
    const tab = url.split('?')[0];
    if (tab === '/pages/order/list' || tab === '/pages/product/list' || tab === '/pages/index/index'
      || tab === '/pages/mine/mine' || tab === '/pages/cart/index') {
      wx.switchTab({ url: tab });
      return;
    }
    wx.navigateTo({ url });
  },

  openAsset(e) {
    wx.navigateTo({ url: '/pages/asset/warranty?id=' + e.currentTarget.dataset.id });
  }
});
