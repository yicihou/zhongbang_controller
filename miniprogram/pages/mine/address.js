const api = require('../../utils/api');
const fmt = require('../../utils/format');

Page({
  data: { list: [], selectMode: false },

  onLoad(q) {
    this.setData({ selectMode: q.select === '1' });
  },

  onShow() {
    this.load();
  },

  load() {
    return api.getAddresses().then((list) => {
      this.setData({
        list: (list || []).map((a) => Object.assign({}, a, { mobileMask: fmt.maskMobile(a.mobile) }))
      });
    });
  },

  pick(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (!this.data.selectMode) return;
    wx.setStorageSync('selectedAddressId', id);
    wx.navigateBack();
  },

  add() {
    wx.navigateTo({ url: '/pages/mine/address-edit' });
  },

  edit(e) {
    wx.navigateTo({ url: '/pages/mine/address-edit?id=' + e.currentTarget.dataset.id });
  },

  remove(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '删除地址',
      content: '删除后不可恢复',
      success: (res) => {
        if (!res.confirm) return;
        api.deleteAddress(id).then(() => this.load());
      }
    });
  },

  setDefault(e) {
    const id = Number(e.currentTarget.dataset.id);
    const row = this.data.list.filter((a) => a.id === id)[0];
    if (!row) return;
    api.updateAddress(id, {
      name: row.name, mobile: row.mobile, province: row.province,
      city: row.city, district: row.district, detail: row.detail, isDefault: true
    }).then(() => this.load());
  },

  importWechat() {
    wx.chooseAddress({
      success: (r) => {
        api.addAddress({
          name: r.userName,
          mobile: r.telNumber,
          province: r.provinceName,
          city: r.cityName,
          district: r.countyName,
          detail: r.detailInfo,
          isDefault: this.data.list.length === 0
        }).then(() => {
          wx.showToast({ title: '已导入', icon: 'success' });
          this.load();
        });
      },
      fail: () => wx.showToast({ title: '未选择微信地址', icon: 'none' })
    });
  }
});
