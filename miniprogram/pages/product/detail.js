const api = require('../../utils/api');
const fmt = require('../../utils/format');

Page({
  data: {
    p: null,
    activeSku: {},
    priceText: '0',
    depositText: '0',
    balanceText: '0'
  },

  onLoad(options) {
    this.id = options.id;
    this.load();
  },

  load() {
    return api.getProduct(this.id).then((p) => {
      const skus = (p.skus || []).map((s) => Object.assign({}, s, { priceText: fmt.yuan(s.price) }));
      const first = skus.filter((s) => s.stock > 0)[0] || skus[0] || {};
      const data = Object.assign({}, p, { skus });
      this.setData({ p: data, activeSku: first }, () => this.calc());
    });
  },

  calc() {
    const sku = this.data.activeSku || {};
    const price = sku.price || 0;
    const deposit = Math.floor(price * (this.data.p.depositRate || 30) / 100);
    this.setData({
      priceText: fmt.yuan(price),
      depositText: fmt.yuan(deposit),
      balanceText: fmt.yuan(price - deposit)
    });
  },

  onSku(e) {
    const id = Number(e.currentTarget.dataset.id);
    const sku = this.data.p.skus.filter((s) => s.id === id)[0];
    if (!sku) return;
    if (sku.stock === 0) {
      wx.showToast({ title: '该配置暂时缺货', icon: 'none' });
      return;
    }
    this.setData({ activeSku: sku }, () => this.calc());
  },

  goConfirm() {
    const sku = this.data.activeSku || {};
    if (!sku.id) return wx.showToast({ title: '请先选择配置', icon: 'none' });
    if (sku.stock === 0) return wx.showToast({ title: '该配置暂时缺货', icon: 'none' });
    const q = [
      'skuId=' + sku.id,
      'skuName=' + encodeURIComponent(sku.name),
      'price=' + sku.price,
      'productName=' + encodeURIComponent(this.data.p.name)
    ].join('&');
    wx.navigateTo({ url: '/pages/order/confirm?' + q });
  }
});
