const KEY = 'invoiceDraft';

Page({
  data: {
    need: false,
    title: '',
    taxNo: ''
  },

  onLoad() {
    const saved = wx.getStorageSync(KEY) || {};
    this.setData({
      need: !!saved.need,
      title: saved.title || '',
      taxNo: saved.taxNo || ''
    });
  },

  onNeed(e) { this.setData({ need: e.detail.value }); },
  onTitle(e) { this.setData({ title: e.detail.value }); },
  onTax(e) { this.setData({ taxNo: e.detail.value }); },

  save() {
    if (this.data.need && !this.data.title.trim()) {
      wx.showToast({ title: '请填写发票抬头', icon: 'none' });
      return;
    }
    wx.setStorageSync(KEY, {
      need: this.data.need,
      title: this.data.title.trim(),
      taxNo: this.data.taxNo.trim()
    });
    wx.showToast({ title: '已保存，下单时会带上', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 400);
  }
});
