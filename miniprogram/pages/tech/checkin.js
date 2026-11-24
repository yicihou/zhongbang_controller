const api = require('../../utils/api');

Page({
  data: { w: {}, submitting: false },

  onLoad(q) {
    this.id = q.id;
    api.getTechWorkorder(this.id).then((w) => this.setData({ w }));
  },

  usePhone() {
    wx.getLocation({
      type: 'gcj02',
      success: (res) => this.submit(res.latitude, res.longitude, '手机定位'),
      fail: () => wx.showToast({ title: '定位失败，可先用下方模拟位置', icon: 'none' })
    });
  },

  useNear() {
    this.submit(30.2742, 120.1552, '模拟定位 · 距客户约 10 米');
  },

  useFar() {
    this.submit(31.2304, 121.4737, '模拟定位 · 上海市');
  },

  submit(latitude, longitude, address) {
    if (this.data.submitting) return;
    this.setData({ submitting: true });
    api.checkinWorkorder(this.id, { latitude, longitude, address }).then((res) => {
      wx.showToast({ title: '打卡成功', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 500);
    }).catch(() => this.setData({ submitting: false }));
  }
});
