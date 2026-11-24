const api = require('../../utils/api');
const fmt = require('../../utils/format');

Page({
  data: {
    keyword: '',
    cats: [],
    activeCat: 0,
    sort: 'default',
    list: [],
    total: 0,
    loading: false
  },

  onLoad(options) {
    if (options && options.categoryId) {
      this.setData({ activeCat: Number(options.categoryId) });
    }
    this.loadCats();
    this.load();
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
      this.setData({
        list: (d.list || []).map((p) => Object.assign({}, p, {
          priceText: fmt.yuan(p.price),
          depositText: fmt.yuan(p.depositAmount),
          tagList: (p.tags || []).slice(0, 2)
        })),
        total: d.total || 0,
        loading: false
      });
    }).catch(() => this.setData({ loading: false }));
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
