const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { deliveryLabel, needInstall } = require('../../utils/constants');

Page({
  data: {
    items: [],
    allSelected: false,
    selectedCount: 0,
    totalText: '0',
    loading: true
  },

  // 回到购物车时重新拉购物车。真实后端还没有购物车表时，这里会提示接口不存在；Mock 开关打开则仍走本地数据。
  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    this.setData({ loading: true });
    return api.getCart().then((d) => {
      const items = (d.items || []).map((it) => Object.assign({}, it, {
        priceText: fmt.yuan(it.price),
        lineText: fmt.yuan(it.price * it.quantity),
        deliveryMethodText: deliveryLabel(it.deliveryMethod),
        needInstall: needInstall(it.deliveryMethod)
      }));
      const selected = items.filter((i) => i.selected);
      const total = selected.reduce((s, i) => s + i.price * i.quantity, 0);
      this.setData({
        items,
        loading: false,
        allSelected: items.length > 0 && selected.length === items.length,
        selectedCount: selected.reduce((s, i) => s + i.quantity, 0),
        totalText: fmt.yuan(total)
      });
    }).catch(() => this.setData({ loading: false }));
  },

  toggle(e) {
    const id = e.currentTarget.dataset.id;
    const row = this.data.items.filter((i) => i.id === id)[0];
    if (!row) return;
    api.updateCartItem(id, { selected: !row.selected }).then(() => this.load());
  },

  toggleAll() {
    const next = !this.data.allSelected;
    Promise.all(this.data.items.map((i) => api.updateCartItem(i.id, { selected: next }))).then(() => this.load());
  },

  changeQty(e) {
    const id = e.currentTarget.dataset.id;
    const step = Number(e.currentTarget.dataset.step);
    const row = this.data.items.filter((i) => i.id === id)[0];
    if (!row) return;
    const quantity = row.quantity + step;
    api.updateCartItem(id, { quantity }).then(() => this.load());
  },

  remove(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '删除',
      content: '从购物车移除这件商品？',
      success: (res) => {
        if (!res.confirm) return;
        api.removeCartItem(id).then(() => this.load());
      }
    });
  },

  goDetail(e) {
    const pid = e.currentTarget.dataset.pid;
    if (pid) wx.navigateTo({ url: '/pages/product/detail?id=' + pid });
  },

  checkout() {
    const selected = this.data.items.filter((i) => i.selected);
    if (!selected.length) return wx.showToast({ title: '请先勾选商品', icon: 'none' });
    wx.setStorageSync('checkoutDraft', {
      from: 'cart',
      cartItemIds: selected.map((i) => i.id),
      items: selected.map((i) => ({
        skuId: i.skuId,
        quantity: i.quantity,
        deliveryMethod: i.deliveryMethod,
        name: i.name,
        skuName: i.skuName,
        price: i.price,
        productId: i.productId,
        kind: i.kind,
        cartItemId: i.id
      }))
    });
    wx.navigateTo({ url: '/pages/order/confirm?from=cart' });
  },

  goShop() {
    wx.switchTab({ url: '/pages/product/list' });
  }
});
