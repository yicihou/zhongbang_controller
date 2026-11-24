const api = require('../../utils/api');
const fmt = require('../../utils/format');
const config = require('../../config');
const cart = require('../../utils/cart');

Page({
  data: {
    skuId: 0,
    skuName: '',
    productName: '',
    priceText: '0',
    depositText: '0',
    balanceText: '0',
    depositRate: 30,
    address: {},
    installDate: '',
    minDate: '',
    slot: 'AM',
    tech: null,
    remark: '',
    submitting: false,
    realMode: !config.USE_MOCK,
    lines: [],
    contactName: '',
    contactMobile: '',
    addressText: '',
    deliveryMethod: 'EXPRESS',
    totalText: '0'
  },

  onLoad(options) {
    const price = Number(options.price || 0);
    const deposit = Math.floor(price * 0.3);
    let lines = [];
    this.fromCart = options.from === 'cart';
    if (!config.USE_MOCK && options.from === 'cart') {
      lines = cart.read();
    } else if (!config.USE_MOCK && options.skuId) {
      lines = [{
        skuId: Number(options.skuId),
        skuName: decodeURIComponent(options.skuName || ''),
        productName: decodeURIComponent(options.productName || ''),
        price: price,
        quantity: 1,
        deliveryMethod: 'EXPRESS'
      }];
    }
    const goods = lines.reduce((sum, row) => sum + Number(row.price || 0) * Number(row.quantity || 1), 0);
    this.setData({
      skuId: Number(options.skuId),
      skuName: decodeURIComponent(options.skuName || ''),
      productName: decodeURIComponent(options.productName || ''),
      priceText: fmt.yuan(price),
      depositText: fmt.yuan(deposit),
      balanceText: fmt.yuan(price - deposit),
      installDate: fmt.dateAfter(3),
      minDate: fmt.dateAfter(1),
      lines: lines.map((row) => Object.assign({}, row, { priceText: fmt.yuan(row.price) })),
      totalText: fmt.yuan(goods)
    });
    this.price = price;
    if (config.USE_MOCK) this.loadAddress();
  },

  onName(e) { this.setData({ contactName: e.detail.value }); },
  onMobile(e) { this.setData({ contactMobile: e.detail.value }); },
  onAddress(e) { this.setData({ addressText: e.detail.value }); },
  onDelivery(e) { this.setData({ deliveryMethod: e.currentTarget.dataset.method }); },

  loadAddress() {
    api.getAddresses().then((list) => {
      const def = (list || []).filter((a) => a.isDefault)[0] || (list || [])[0] || {};
      this.setData({ address: def });
    });
  },

  chooseAddress() {
    const list = this.addresses || [];
    wx.showToast({ title: '地址管理页可新增/切换', icon: 'none' });
  },

  onDate(e) {
    this.setData({ installDate: e.detail.value });
  },

  onSlot(e) {
    this.setData({ slot: e.currentTarget.dataset.slot });
  },

  onRemark(e) {
    this.setData({ remark: e.detail.value });
  },

  chooseTech() {
    wx.navigateTo({
      url: '/pages/order/tech?skuId=' + this.data.skuId + '&date=' + this.data.installDate,
      events: {
        selectTech: (t) => this.setData({ tech: t })
      }
    });
  },

  submit() {
    if (this.data.submitting) return;
    if (!config.USE_MOCK) return this.submitReal();
    if (!this.data.address.id) return wx.showToast({ title: '请先选择收货地址', icon: 'none' });
    this.setData({ submitting: true });

    const payload = {
      requestId: 'r' + Date.now() + Math.floor(Math.random() * 1000),
      skuId: this.data.skuId,
      quantity: 1,
      addressId: this.data.address.id,
      installDate: this.data.installDate,
      installSlot: this.data.slot,
      technicianId: this.data.tech ? this.data.tech.id : null,
      remark: this.data.remark
    };

    api.createOrder(payload).then((res) => {
      return this.pay(res.orderId, 'DEPOSIT').then(() => {
        wx.redirectTo({ url: '/pages/order/detail?id=' + res.orderId + '&paid=1' });
      }).catch(() => {
        wx.redirectTo({ url: '/pages/order/detail?id=' + res.orderId });
      });
    }).catch(() => {
      this.setData({ submitting: false });
    });
  },

  submitReal() {
    const lines = this.data.lines || [];
    if (!lines.length) return wx.showToast({ title: '请先选择商品', icon: 'none' });
    if (!this.data.contactName || !this.data.contactMobile || !this.data.addressText) {
      return wx.showToast({ title: '请填写收货人和地址', icon: 'none' });
    }
    this.setData({ submitting: true });
    const method = this.data.deliveryMethod;
    const payload = {
      requestId: 'r' + Date.now() + Math.floor(Math.random() * 1000),
      contactName: this.data.contactName,
      contactMobile: this.data.contactMobile,
      addressText: this.data.addressText,
      remark: this.data.remark,
      items: lines.map((row) => ({
        skuId: row.skuId,
        quantity: Number(row.quantity || 1),
        deliveryMethod: method,
        installDate: method === 'ON_SITE_INSTALL' ? this.data.installDate : '',
        installSlot: method === 'ON_SITE_INSTALL' ? this.data.slot : '',
        technicianId: method === 'ON_SITE_INSTALL' && this.data.tech ? this.data.tech.id : null
      }))
    };
    api.createOrder(payload).then((res) => {
      if (this.fromCart) cart.clear();
      wx.redirectTo({ url: '/pages/order/detail?id=' + res.orderId });
    }).catch(() => {
      this.setData({ submitting: false });
    });
  },

  pay(orderId, type) {
    return api.prepay(orderId, type).then((p) => {
      if (config.USE_MOCK) {
        return api.mockPaySuccess(orderId, type);
      }
      return new Promise((resolve, reject) => {
        wx.requestPayment({
          timeStamp: p.payParams.timeStamp,
          nonceStr: p.payParams.nonceStr,
          package: p.payParams.package,
          signType: p.payParams.signType,
          paySign: p.payParams.paySign,
          success: resolve,
          fail: reject
        });
      });
    });
  }
});
