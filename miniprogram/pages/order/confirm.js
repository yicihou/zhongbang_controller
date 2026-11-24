const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { DELIVERY_OPTIONS, DeliveryMethod, needInstall, deliveryLabel } = require('../../utils/constants');

Page({
  data: {
    items: [],
    address: {},
    remark: '',
    invoice: { need: false, title: '', taxNo: '' },
    manualAddress: false,
    contactName: '',
    contactMobile: '',
    addressText: '',
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
    // 地址簿在 Mock 里有。真实后端还没有这张表时，改成手填，下单仍走 /api/orders。
    api.getAddresses().then((list) => {
      this.applyAddresses(list || []);
    }).catch(() => this.applyAddresses([]));
  },

  applyAddresses(rows) {
    const picked = Number(wx.getStorageSync('selectedAddressId') || 0);
    const address = rows.filter((a) => a.id === picked)[0]
      || rows.filter((a) => a.isDefault)[0]
      || rows[0]
      || {};
    if (address.id) address.mobileMask = fmt.maskMobile(address.mobile);
    this.setData({ address, manualAddress: !address.id });
  },

  onName(e) { this.setData({ contactName: e.detail.value }); },
  onMobile(e) { this.setData({ contactMobile: e.detail.value }); },
  onAddressText(e) { this.setData({ addressText: e.detail.value }); },

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
    if (!this.data.address.id) {
      if (!this.data.contactName || !this.data.contactMobile || !this.data.addressText) {
        return wx.showToast({ title: '请填写收货人和地址', icon: 'none' });
      }
    }
    for (let i = 0; i < this.data.items.length; i++) {
      const it = this.data.items[i];
      if (it.needInstall && (!it.installDate || !it.installSlot)) {
        return wx.showToast({ title: '请完善上门安装日期与时段', icon: 'none' });
      }
    }
    this.setData({ submitting: true });
    // 提交前再问一次商品：下架或价格变了就停住，让客户看到新价格后再点一次。
    this.ensureFreshPrice().then((ok) => {
      if (!ok) {
        this.setData({ submitting: false });
        return;
      }
      this.placeOrder();
    }).catch(() => this.setData({ submitting: false }));
  },

  ensureFreshPrice() {
    const items = this.data.items || [];
    const ids = [];
    items.forEach((it) => {
      if (it.productId && ids.indexOf(it.productId) < 0) ids.push(it.productId);
    });
    if (!ids.length) return Promise.resolve(true);
    return Promise.all(ids.map((id) => api.getProduct(id).then((p) => ({ id: id, p: p })).catch(() => ({ id: id, p: null }))))
      .then((rows) => {
        let offName = '';
        let changed = false;
        const next = items.map((it) => Object.assign({}, it));
        next.forEach((it) => {
          if (!it.productId) return;
          const hit = rows.filter((row) => row.id === it.productId)[0];
          const product = hit && hit.p;
          const sku = product && (product.skus || []).filter((s) => s.id === it.skuId)[0];
          if (!product || product.status === 'OFF' || !sku) {
            offName = it.name || '商品';
            return;
          }
          if (sku.price !== it.price) {
            it.price = sku.price;
            it.priceText = fmt.yuan(sku.price);
            changed = true;
          }
        });
        if (offName) {
          wx.showToast({ title: offName + '已下架，请重新选择', icon: 'none' });
          return false;
        }
        if (changed) {
          this.setData({ items: next }, () => this.recalc());
          wx.showModal({
            title: '价格已变化',
            content: '商品价格已更新为最新价格，请确认后再提交订单',
            showCancel: false
          });
          return false;
        }
        return true;
      });
  },

  placeOrder() {
    const invoice = this.data.invoice || {};
    const body = {
      requestId: 'r' + Date.now() + Math.floor(Math.random() * 1000),
      remark: this.data.remark,
      invoice: { need: !!invoice.need, title: invoice.title || '', taxNo: invoice.taxNo || '' },
      items: this.data.items.map((it) => {
        const row = {
          skuId: it.skuId,
          quantity: it.quantity,
          deliveryMethod: it.deliveryMethod,
          expectedPrice: it.price
        };
        if (it.needInstall) {
          row.installDate = it.installDate;
          row.installSlot = it.installSlot;
          if (it.technicianId) row.technicianId = it.technicianId;
        }
        return row;
      })
    };
    if (this.data.address.id) body.addressId = this.data.address.id;
    else {
      body.contactName = this.data.contactName;
      body.contactMobile = this.data.contactMobile;
      body.addressText = this.data.addressText;
    }
    if (this.cartItemIds && this.cartItemIds.length) body.cartItemIds = this.cartItemIds;
    api.createOrder(body).then((res) => {
      try { wx.removeStorageSync('checkoutDraft'); } catch (e) { /* ignore */ }
      wx.redirectTo({
        url: '/pages/order/detail?id=' + res.orderId + '&created=1'
      });
    }).catch(() => this.setData({ submitting: false }));
  }
});
