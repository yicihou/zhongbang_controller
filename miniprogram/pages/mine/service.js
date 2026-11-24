const config = require('../../config');

Page({
  data: {
    phone: config.servicePhone,
    phoneText: '400-000-0000',
    wechat: config.serviceWechat
  },

  call() {
    wx.makePhoneCall({ phoneNumber: this.data.phone });
  },

  copyWx() {
    wx.setClipboardData({ data: this.data.wechat });
  }
});
