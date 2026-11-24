const api = require('../../utils/api');
const fmt = require('../../utils/format');

Page({
  data: {
    keyword: '',
    cats: [],
    activeCat: 0,
    sort: 'default',
    bands: [
      { label: '全部价格', value: 'all' },
      { label: '5千以下', value: 'lt' },
      { label: '5千-1万', value: 'mid' },
      { label: '1万以上', value: 'gt' }
    ],
    band: 'all',
    raw: [],
    list: [],
    total: 0,
    loading: false
  },

  onLoad(options) {
    if (options && options.categoryId) {
      this.setData({ activeCat: Number(options.categoryId) });
    }
    this.loadCats();
  },

  // 从别的页回来、或管理员刚改了上下架和价格时，重新拉一次列表。
  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  loadCats() {
    api.getCategories().then((list) => this.setData({ cats: list || [] }));
  },

  load() {
    this.setData({ loading: true });
    const params = { sort: this.data.sort };
    if (this.data.activeCat) params.categoryId = this.data.activeCat;
    if (this.data.keyword) params.keyword = this.data.keyword;
    return api.getProducts(params).then((d) => {
      const raw = (d.list || []).map((p) => Object.assign({}, p, {
        priceText: fmt.yuan(p.price),
        depositText: fmt.yuan(p.depositAmount),
        tagList: (p.tags || []).slice(0, 2)
      }));
      this.setData({ raw, total: d.total || 0, loading: false }, () => this.applyBand());
    }).catch(() => this.setData({ loading: false }));
  },

  applyBand() {
    const band = this.data.band;
    let list = this.data.raw || [];
    if (band === 'lt') list = list.filter((p) => p.price < 500000);
    else if (band === 'mid') list = list.filter((p) => p.price >= 500000 && p.price < 1000000);
    else if (band === 'gt') list = list.filter((p) => p.price >= 1000000);
    this.setData({ list });
  },

  onBand(e) {
    this.setData({ band: e.currentTarget.dataset.band }, () => this.applyBand());
  },

  onCat(e) {
    this.setData({ activeCat: Number(e.currentTarget.dataset.id) });
    this.load();
  },

  onSort(e) {
    this.setData({ sort: e.currentTarget.dataset.sort });
    this.load();
  },

  onSearchTap() {
    wx.showModal({
      title: '搜索机型',
      editable: true,
      placeholderText: '输入机型名称或型号，如 A5',
      success: (res) => {
        if (res.confirm) {
          this.setData({ keyword: (res.content || '').trim() });
          this.load();
        }
      }
    });
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/product/detail?id=' + e.currentTarget.dataset.id });
  }
});
