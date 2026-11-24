const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { DeliveryMethod } = require('../../utils/constants');

Page({
  data: {
    p: null,
    groups: [],
    selected: {},
    activeSku: {},
    priceText: '0',
    quantity: 1,
    adding: false
  },

  onLoad(options) {
    this.id = options.id;
    this.load();
  },

  load() {
    return api.getProduct(this.id).then((p) => {
      const skus = (p.skus || []).map((s) => Object.assign({}, s, { priceText: fmt.yuan(s.price) }));
      const data = Object.assign({}, p, { skus });
      const built = this.buildGroups(skus);
      this.setData({ p: data, groups: built.groups, selected: built.selected, activeSku: built.sku }, () => this.calc());
    });
  },

  buildGroups(skus) {
    const dims = [];
    skus.forEach((s) => {
      Object.keys(s.specs || {}).forEach((k) => {
        if (dims.indexOf(k) < 0) dims.push(k);
      });
    });
    const base = skus.filter((s) => s.stock > 0).slice().sort((a, b) => a.price - b.price)[0] || skus[0] || {};
    const selected = Object.assign({}, base.specs || {});
    if (!dims.length) return { groups: [], selected: {}, sku: base };
    const groups = dims.map((name) => {
      const values = [];
      skus.forEach((s) => {
        const v = (s.specs || {})[name];
        if (v && values.indexOf(v) < 0) values.push(v);
      });
      return {
        name,
        options: values.map((value) => {
          const probe = Object.assign({}, base.specs || {}, { [name]: value });
          const alt = skus.filter((s) => dims.every((d) => (s.specs || {})[d] === probe[d]))[0];
          let extra = '';
          if (alt && base.price !== undefined && alt.price !== base.price) {
            const diff = alt.price - base.price;
            extra = (diff > 0 ? ' +' : ' ') + fmt.yuan(diff);
          }
          return { value, label: value + extra, out: !!(alt && alt.stock === 0) };
        })
      };
    });
    const sku = this.findSku(skus, dims, selected) || base;
    return { groups: this.mark(groups, selected), selected, sku };
  },

  mark(groups, selected) {
    return (groups || []).map((g) => ({
      name: g.name,
      options: (g.options || []).map((opt) => Object.assign({}, opt, { on: selected[g.name] === opt.value }))
    }));
  },

  findSku(skus, dims, selected) {
    return (skus || []).filter((s) => dims.every((d) => (s.specs || {})[d] === selected[d]))[0];
  },

  calc() {
    const sku = this.data.activeSku || {};
    this.setData({ priceText: fmt.yuan(sku.price || 0) });
  },

  onOpt(e) {
    const dim = e.currentTarget.dataset.dim;
    const value = e.currentTarget.dataset.value;
    const selected = Object.assign({}, this.data.selected, { [dim]: value });
    const dims = this.data.groups.map((g) => g.name);
    const sku = this.findSku(this.data.p.skus, dims, selected);
    if (!sku) {
      wx.showToast({ title: '这个组合暂不销售', icon: 'none' });
      return;
    }
    this.setData({
      selected,
      activeSku: sku,
      groups: this.mark(this.data.groups, selected)
    }, () => this.calc());
    if (sku.stock === 0) wx.showToast({ title: '该配置暂时缺货', icon: 'none' });
  },

  onSku(e) {
    const id = Number(e.currentTarget.dataset.id);
    const sku = this.data.p.skus.filter((s) => s.id === id)[0];
    if (!sku) return;
    this.setData({ activeSku: sku, selected: sku.specs || {} }, () => this.calc());
    if (sku.stock === 0) wx.showToast({ title: '该配置暂时缺货', icon: 'none' });
  },

  changeQty(e) {
    const step = Number(e.currentTarget.dataset.step);
    const quantity = Math.max(1, Math.min(99, this.data.quantity + step));
    this.setData({ quantity });
  },

  notify() {
    wx.showToast({ title: '已登记，到货后将用订阅消息通知', icon: 'none' });
  },

  defaultDelivery() {
    return (this.data.p && this.data.p.kind === 'PART')
      ? DeliveryMethod.EXPRESS
      : DeliveryMethod.ON_SITE_INSTALL;
  },

  ensureSku() {
    const sku = this.data.activeSku || {};
    if (!sku.id) {
      wx.showToast({ title: '请先选择配置', icon: 'none' });
      return null;
    }
    if (sku.stock === 0) {
      this.notify();
      return null;
    }
    return sku;
  },

  addCart() {
    const sku = this.ensureSku();
    if (!sku || this.data.adding) return;
    this.setData({ adding: true });
    api.addCartItem({
      skuId: sku.id,
      quantity: this.data.quantity,
      deliveryMethod: this.defaultDelivery()
    }).then(() => {
      this.setData({ adding: false });
      wx.showToast({ title: '已加入购物车', icon: 'success' });
    }).catch(() => this.setData({ adding: false }));
  },

  buyNow() {
    const sku = this.ensureSku();
    if (!sku) return;
    const method = this.defaultDelivery();
    wx.setStorageSync('checkoutDraft', {
      from: 'buy',
      items: [{
        skuId: sku.id,
        quantity: this.data.quantity,
        deliveryMethod: method,
        name: this.data.p.name,
        skuName: sku.name,
        price: sku.price,
        productId: this.data.p.id,
        kind: this.data.p.kind
      }]
    });
    wx.navigateTo({ url: '/pages/order/confirm?from=buy' });
  },

  goCart() {
    wx.switchTab({ url: '/pages/cart/index' });
  }
});
