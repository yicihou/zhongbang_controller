const api = require('../../utils/api');
const fmt = require('../../utils/format');

Page({
  data: {
    p: null,
    groups: [],
    selected: {},
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
      const data = Object.assign({}, p, { skus });
      const built = this.buildGroups(skus);
      this.setData({ p: data, groups: built.groups, selected: built.selected, activeSku: built.sku }, () => this.calc());
    });
  },

  /** 把 SKU 的 specs 拆成「台板 / 电机」这类维度，价格差相对默认可售配置 */
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
          return {
            value,
            label: value + extra,
            out: !!(alt && alt.stock === 0)
          };
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
    const price = sku.price || 0;
    const deposit = Math.floor(price * ((this.data.p && this.data.p.depositRate) || 30) / 100);
    this.setData({
      priceText: fmt.yuan(price),
      depositText: fmt.yuan(deposit),
      balanceText: fmt.yuan(price - deposit)
    });
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

  notify() {
    wx.showToast({ title: '已登记，到货后将用订阅消息通知', icon: 'none' });
  },

  goConfirm() {
    const sku = this.data.activeSku || {};
    if (!sku.id) return wx.showToast({ title: '请先选择配置', icon: 'none' });
    if (sku.stock === 0) return this.notify();
    const q = [
      'skuId=' + sku.id,
      'skuName=' + encodeURIComponent(sku.name),
      'price=' + sku.price,
      'depositRate=' + (this.data.p.depositRate || 30),
      'productName=' + encodeURIComponent(this.data.p.name)
    ].join('&');
    wx.navigateTo({ url: '/pages/order/confirm?' + q });
  }
});
