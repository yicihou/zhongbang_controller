const api = require('../../utils/api');
const fmt = require('../../utils/format');

Page({
  data: {
    banner: null,
    entries: [],
    hot: [],
    total: 0
  },

  onLoad() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    return api.getHome().then((d) => {
      const cats = d.categories || [];
      this.setData({
        banner: (d.banners || [])[0] || null,
        entries: d.quickEntries || [],
        total: cats.reduce((s, c) => s + (c.count || 0), 0),
        hot: (d.hotProducts || []).map((p) => Object.assign({}, p, {
          priceText: fmt.yuan(p.price),
          depositText: fmt.yuan(p.depositAmount),
          tag: (p.tags || [])[0] || ''
        }))
      });
    });
  },

  goList() {
    wx.switchTab({ url: '/pages/product/list' });
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/product/detail?id=' + e.currentTarget.dataset.id });
  },

  onSearch() {
    wx.switchTab({ url: '/pages/product/list' });
  },

  onQuick(e) {
    const code = e.currentTarget.dataset.code;
    if (code === 'BUY') return wx.switchTab({ url: '/pages/product/list' });
    if (code === 'REPAIR') return wx.navigateTo({ url: '/pages/repair/create' });
    if (code === 'ASSET') return wx.navigateTo({ url: '/pages/asset/list' });
    if (code === 'INVOICE') return wx.navigateTo({ url: '/pages/mine/invoice' });
  }
});
