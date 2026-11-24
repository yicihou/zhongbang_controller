const api = require('../../utils/api');
const fmt = require('../../utils/format');
const config = require('../../config');

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
    submitting: false
  },

  onLoad(options) {
    const price = Number(options.price || 0);
    const deposit = Math.floor(price * 0.3);
    this.setData({
      skuId: Number(options.skuId),
      skuName: decodeURIComponent(options.skuName || ''),
      productName: decodeURIComponent(options.productName || ''),
      priceText: fmt.yuan(price),
      depositText: fmt.yuan(deposit),
      balanceText: fmt.yuan(price - deposit),
      installDate: fmt.dateAfter(3),
      minDate: fmt.dateAfter(1)
    });
    this.price = price;
    this.loadAddress();
  },

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
