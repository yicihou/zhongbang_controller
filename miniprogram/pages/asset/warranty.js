const api = require('../../utils/api');

Page({
  data: { a: {} },

  onLoad(q) {
    this.id = Number(q.id);
    api.getAssets().then((list) => {
      const a = (list || []).filter((x) => x.id === this.id)[0] || {};
      this.setData({ a });
    });
  },

  repair() {
    wx.navigateTo({ url: '/pages/repair/create?assetId=' + this.id });
  }
});
