const api = require('../../utils/api');
const fmt = require('../../utils/format');
const config = require('../../config');

Page({
  data: {
    profile: { customer: {} },
    assets: [],
    pendingText: '0',
    initial: '微',
    version: config.version
  },

  onShow() {
    this.load();
  },

  load() {
    api.getProfile().then((p) => {
      const name = (p.customer && p.customer.nickname) || '微';
      this.setData({
        profile: p,
        pendingText: fmt.yuan(p.pendingAmount),
        initial: name.slice(0, 1)
      });
    });
    api.getAssets().then((list) => this.setData({ assets: list || [] }));
  },

  goOrders() {
    wx.switchTab({ url: '/pages/order/list' });
  },

  goRepair() {
    wx.navigateTo({ url: '/pages/repair/create' });
  },

  goTech() {
    wx.navigateTo({ url: '/pages/tech/worklist' });
  },

  contact() {
    wx.makePhoneCall({ phoneNumber: '4000000000' });
  }
});
