const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { DELIVERY_OPTIONS, DeliveryMethod, needInstall, deliveryLabel } = require('../../utils/constants');

Page({
  data: {
    items: [],
    address: {},
    remark: '',
    invoice: { need: false, title: '', taxNo: '' },
    deliveryOptions: DELIVERY_OPTIONS,
    goodsText: '0',
    freightTip: '默认 0，管理员后加',
    installTip: '上门安装行安装费待管理员确认',
    minDate: '',
    submitting: false,
    fromCart: false
  },

  onLoad(options) {
    const draft = wx.getStorageSync('checkoutDraft') || {};
    const items = (draft.items || []).map((it, idx) => this.decorateItem(it, idx));
    if (!items.length && options.skuId) {
      // 兼容旧单行跳转
      items.push(this.decorateItem({
        skuId: Number(options.skuId),
        quantity: 1,
        deliveryMethod: DeliveryMethod.ON_SITE_INSTALL,
        name: decodeURIComponent(options.productName || ''),
        skuName: decodeURIComponent(options.skuName || ''),
        price: Number(options.price || 0)
      }, 0));
    }
    this.cartItemIds = draft.cartItemIds || [];
    this.setData({
      items,
      fromCart: draft.from === 'cart' || options.from === 'cart',
      minDate: fmt.dateAfter(1)
    }, () => this.recalc());
  },

  onShow() {
    const invoice = wx.getStorageSync('invoiceDraft') || { need: false, title: '', taxNo: '' };
    this.setData({ invoice });
    const techPick = wx.getStorageSync('confirmTechPick');
    if (techPick && techPick.lineIndex !== undefined) {
      wx.removeStorageSync('confirmTechPick');
      const items = this.data.items.slice();
      const row = items[techPick.lineIndex];
      if (row) {
        row.technicianId = techPick.id;
        row.technicianName = techPick.name;
        items[techPick.lineIndex] = row;
        this.setData({ items });
      }
    }
    api.getAddresses().then((list) => {
      const rows = list || [];
      const picked = Number(wx.getStorageSync('selectedAddressId') || 0);
      const address = rows.filter((a) => a.id === picked)[0]
        || rows.filter((a) => a.isDefault)[0]
        || rows[0]
        || {};
      if (address.id) address.mobileMask = fmt.maskMobile(address.mobile);
      this.setData({ address });
    });
  },

  decorateItem(it, idx) {
    const method = it.deliveryMethod || DeliveryMethod.EXPRESS;
    return {
      key: idx,
      skuId: it.skuId,
      quantity: it.quantity || 1,
      deliveryMethod: method,
      deliveryMethodText: deliveryLabel(method),
      name: it.name || '',
      skuName: it.skuName || '',
      price: it.price || 0,
      priceText: fmt.yuan(it.price || 0),
      productId: it.productId,
      kind: it.kind,
      cartItemId: it.cartItemId,
      needInstall: needInstall(method),
      installDate: it.installDate || fmt.dateAfter(3),
      installSlot: it.installSlot || 'AM',
      technicianId: it.technicianId || null,
      technicianName: it.technicianName || ''
    };
  },

  recalc() {
    const goods = this.data.items.reduce((s, it) => s + it.price * it.quantity, 0);
    this.setData({ goodsText: fmt.yuan(goods) });
  },

  changeQty(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const step = Number(e.currentTarget.dataset.step);
    const items = this.data.items.slice();
    const row = items[idx];
    if (!row) return;
    row.quantity = Math.max(1, Math.min(99, row.quantity + step));
    items[idx] = row;
    this.setData({ items }, () => this.recalc());
  },

  onDelivery(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const method = e.currentTarget.dataset.method;
    const items = this.data.items.slice();
    const row = items[idx];
    if (!row) return;
    row.deliveryMethod = method;
    row.deliveryMethodText = deliveryLabel(method);
    row.needInstall = needInstall(method);
    if (row.needInstall && !row.installDate) {
      row.installDate = fmt.dateAfter(3);
      row.installSlot = 'AM';
    }
    if (!row.needInstall) {
      row.technicianId = null;
      row.technicianName = '';
    }
    items[idx] = row;
    this.setData({ items });
  },

  onDate(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const items = this.data.items.slice();
    items[idx].installDate = e.detail.value;
    items[idx].technicianId = null;
    items[idx].technicianName = '';
    this.setData({ items });
  },

  onSlot(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const items = this.data.items.slice();
    items[idx].installSlot = e.currentTarget.dataset.slot;
    items[idx].technicianId = null;
    items[idx].technicianName = '';
    this.setData({ items });
  },

  chooseTech(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const row = this.data.items[idx];
    if (!row) return;
    wx.setStorageSync('confirmTechLineIndex', idx);
    wx.navigateTo({
      url: '/pages/order/tech?skuId=' + row.skuId
        + '&date=' + row.installDate
        + '&slot=' + row.installSlot
        + '&lineIndex=' + idx,
      events: {
        selectTech: (t) => {
          const items = this.data.items.slice();
          items[idx].technicianId = t.id;
          items[idx].technicianName = t.name;
          this.setData({ items });
        }
      }
    });
  },

  chooseAddress() {
    wx.navigateTo({ url: '/pages/mine/address?select=1' });
  },

  editInvoice() {
    wx.navigateTo({ url: '/pages/mine/invoice' });
  },

  onRemark(e) {
    this.setData({ remark: e.detail.value });
  },

  submit() {
    if (this.data.submitting) return;
    if (!this.data.items.length) return wx.showToast({ title: '没有可结算商品', icon: 'none' });
    if (!this.data.address.id) return wx.showToast({ title: '请先选择收货地址', icon: 'none' });
    for (let i = 0; i < this.data.items.length; i++) {
      const it = this.data.items[i];
      if (it.needInstall && (!it.installDate || !it.installSlot)) {
        return wx.showToast({ title: '请完善上门安装日期与时段', icon: 'none' });
      }
    }
    this.setData({ submitting: true });
    const invoice = this.data.invoice || {};
    const body = {
      requestId: 'r' + Date.now() + Math.floor(Math.random() * 1000),
      addressId: this.data.address.id,
      remark: this.data.remark,
      invoice: { need: !!invoice.need, title: invoice.title || '', taxNo: invoice.taxNo || '' },
      items: this.data.items.map((it) => {
        const row = {
          skuId: it.skuId,
          quantity: it.quantity,
          deliveryMethod: it.deliveryMethod
        };
        if (it.needInstall) {
          row.installDate = it.installDate;
          row.installSlot = it.installSlot;
          if (it.technicianId) row.technicianId = it.technicianId;
        }
        return row;
      })
    };
    if (this.cartItemIds && this.cartItemIds.length) body.cartItemIds = this.cartItemIds;
    api.createOrder(body).then((res) => {
      try { wx.removeStorageSync('checkoutDraft'); } catch (e) { /* ignore */ }
      wx.redirectTo({
        url: '/pages/order/detail?id=' + res.orderId + '&created=1'
      });
    }).catch(() => this.setData({ submitting: false }));
  }
});
