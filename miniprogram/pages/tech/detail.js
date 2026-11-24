const api = require('../../utils/api');
const fmt = require('../../utils/format');

const REJECTS = ['时间冲突', '距离太远', '技能不匹配', '客户临时改期'];

Page({
  data: {
    w: { timeline: [] },
    date: '',
    slot: 'AM',
    minDate: ''
  },

  onLoad(q) {
    this.id = q.id;
    this.setData({ date: fmt.dateAfter(1), minDate: fmt.dateAfter(0) });
  },

  onShow() {
    this.load();
  },

  load() {
    return api.getTechWorkorder(this.id).then((w) => this.setData({ w }));
  },

  onDate(e) { this.setData({ date: e.detail.value }); },
  onSlot(e) { this.setData({ slot: e.currentTarget.dataset.slot }); },

  accept() {
    const at = this.data.date + (this.data.slot === 'PM' ? ' 14:00:00' : ' 09:30:00');
    api.acceptWorkorder(this.id, { appointmentAt: at }).then(() => {
      wx.showToast({ title: '已接单', icon: 'success' });
      this.load();
    });
  },

  reject() {
    wx.showActionSheet({
      itemList: REJECTS,
      success: (res) => {
        api.rejectWorkorder(this.id, REJECTS[res.tapIndex]).then(() => {
          wx.showToast({ title: '已拒单', icon: 'none' });
          this.load();
        }).catch(() => {});
      }
    });
  },

  navigate() {
    const w = this.data.w;
    wx.openLocation({
      latitude: Number(w.latitude) || 30.27415,
      longitude: Number(w.longitude) || 120.15515,
      name: w.customerName || '客户',
      address: w.address || '',
      scale: 16
    });
  },

  goCheckin() {
    wx.navigateTo({ url: '/pages/tech/checkin?id=' + this.id });
  },

  goForm() {
    const path = this.data.w.type === 'INSTALL' ? '/pages/tech/install' : '/pages/tech/repair';
    wx.navigateTo({ url: path + '?id=' + this.id });
  }
});
