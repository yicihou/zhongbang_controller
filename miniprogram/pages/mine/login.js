const api = require('../../utils/api');
const auth = require('../../utils/auth');

Page({
  data: {
    mobile: '',
    agreed: false
  },

  onShow() {
    const agreed = !!wx.getStorageSync('privacyAgreed');
    this.setData({ agreed });
    api.getProfile().then((p) => {
      this.setData({ mobile: (p.customer && p.customer.mobile) || '' });
    }).catch(() => {});
  },

  toggle() {
    const agreed = !this.data.agreed;
    this.setData({ agreed });
    if (agreed) wx.setStorageSync('privacyAgreed', true);
  },

  goPrivacy() {
    wx.navigateTo({ url: '/pages/mine/privacy' });
  },

  onPhone(e) {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读隐私说明', icon: 'none' });
      return;
    }
    auth.bindPhone(e).then((res) => {
      this.setData({ mobile: (res && res.mobile) || '138****8000' });
      wx.showToast({ title: '手机号已绑定', icon: 'success' });
    }).catch(() => {});
  }
});
