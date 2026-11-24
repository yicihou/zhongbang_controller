const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { pay } = require('../../utils/pay');

Page({
  data: {
    skuId: 0,
    skuName: '',
    productName: '',
    quantity: 1,
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
    invoice: { need: false, title: '', taxNo: '' },
    submitting: false
  },

  onLoad(options) {
    const price = Number(options.price || 0);
    const depositRate = Number(options.depositRate || 30);
    this.unitPrice = price;
    this.setData({
      skuId: Number(options.skuId),
      skuName: decodeURIComponent(options.skuName || ''),
      productName: decodeURIComponent(options.productName || ''),
      depositRate,
      installDate: fmt.dateAfter(3),
      minDate: fmt.dateAfter(1)
    });
    this.recalc();
  },

  onShow() {
    const invoice = wx.getStorageSync('invoiceDraft') || { need: false, title: '', taxNo: '' };
    this.setData({ invoice });
    api.getAddresses().then((list) => {
      const rows = list || [];
      const picked = Number(wx.getStorageSync('selectedAddressId') || 0);
      const address = rows.filter((a) => a.id === picked)[0]
        || rows.filter((a) => a.isDefault)[0]
        || rows[0]
        || {};
      if (address.id) {
        address.mobileMask = fmt.maskMobile(address.mobile);
      }
      this.setData({ address });
    });
  },

  recalc() {
    const total = this.unitPrice * this.data.quantity;
    const deposit = Math.floor(total * this.data.depositRate / 100);
    this.depositCents = deposit;
    this.setData({
      priceText: fmt.yuan(total),
      depositText: fmt.yuan(deposit),
      balanceText: fmt.yuan(total - deposit)
    });
  },

  changeQty(e) {
    const step = Number(e.currentTarget.dataset.step);
    const quantity = Math.max(1, Math.min(9, this.data.quantity + step));
    this.setData({ quantity }, () => this.recalc());
  },

  chooseAddress() {
    wx.navigateTo({ url: '/pages/mine/address?select=1' });
  },

  editInvoice() {
    wx.navigateTo({ url: '/pages/mine/invoice' });
  },

  onDate(e) {
    this.setData({ installDate: e.detail.value, tech: null });
  },

  onSlot(e) {
    this.setData({ slot: e.currentTarget.dataset.slot, tech: null });
  },

  onRemark(e) {
    this.setData({ remark: e.detail.value });
  },

  chooseTech() {
    wx.navigateTo({
      url: '/pages/order/tech?skuId=' + this.data.skuId
        + '&date=' + this.data.installDate
        + '&slot=' + this.data.slot
        + '&deposit=' + this.depositCents,
      events: {
        selectTech: (t) => this.setData({ tech: t })
      }
    });
  },

  submit() {
    if (this.data.submitting) return;
    if (!this.data.address.id) return wx.showToast({ title: '请先选择收货地址', icon: 'none' });
    this.setData({ submitting: true });
    const invoice = this.data.invoice || {};
    api.createOrder({
      requestId: 'r' + Date.now() + Math.floor(Math.random() * 1000),
      skuId: this.data.skuId,
      quantity: this.data.quantity,
      addressId: this.data.address.id,
      installDate: this.data.installDate,
      installSlot: this.data.slot,
      technicianId: this.data.tech ? this.data.tech.id : null,
      remark: this.data.remark,
      invoice: { need: !!invoice.need, title: invoice.title || '', taxNo: invoice.taxNo || '' }
    }).then((res) => {
      return pay(res.orderId, 'DEPOSIT').then(() => {
        wx.redirectTo({ url: '/pages/order/result?orderId=' + res.orderId + '&type=DEPOSIT' });
      }).catch(() => {
        wx.redirectTo({ url: '/pages/order/detail?id=' + res.orderId });
      });
    }).catch(() => this.setData({ submitting: false }));
  }
});
