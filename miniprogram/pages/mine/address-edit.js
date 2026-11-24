const api = require('../../utils/api');

Page({
  data: {
    id: 0,
    name: '',
    mobile: '',
    region: ['浙江省', '杭州市', '余杭区'],
    regionText: '浙江省 杭州市 余杭区',
    detail: '',
    isDefault: false
  },

  onLoad(q) {
    if (!q.id) {
      wx.setNavigationBarTitle({ title: '新增地址' });
      return;
    }
    this.setData({ id: Number(q.id) });
    api.getAddresses().then((list) => {
      const a = (list || []).filter((x) => x.id === Number(q.id))[0];
      if (!a) return;
      const region = [a.province, a.city, a.district];
      this.setData({
        name: a.name,
        mobile: a.mobile,
        region,
        regionText: region.join(' '),
        detail: a.detail,
        isDefault: !!a.isDefault
      });
    });
  },

  onName(e) { this.setData({ name: e.detail.value }); },
  onMobile(e) { this.setData({ mobile: e.detail.value }); },
  onDetail(e) { this.setData({ detail: e.detail.value }); },
  onRegion(e) {
    const region = e.detail.value;
    this.setData({ region, regionText: region.join(' ') });
  },
  onDefault(e) { this.setData({ isDefault: e.detail.value }); },

  locate() {
    wx.chooseLocation({
      success: (r) => {
        const extra = ((r.address || '') + (r.name || '')).trim();
        this.setData({ detail: extra || this.data.detail });
      },
      fail: () => wx.showToast({ title: '未选择位置', icon: 'none' })
    });
  },

  save() {
    const d = this.data;
    if (!d.name.trim()) return wx.showToast({ title: '请填写联系人', icon: 'none' });
    if (!/^1\d{10}$/.test(d.mobile)) return wx.showToast({ title: '请填写 11 位手机号', icon: 'none' });
    if (!d.detail.trim()) return wx.showToast({ title: '请填写详细地址', icon: 'none' });
    const body = {
      name: d.name.trim(),
      mobile: d.mobile.trim(),
      province: d.region[0],
      city: d.region[1],
      district: d.region[2],
      detail: d.detail.trim(),
      isDefault: d.isDefault
    };
    const req = d.id ? api.updateAddress(d.id, body) : api.addAddress(body);
    req.then((res) => {
      if (!d.id && res && res.id) wx.setStorageSync('selectedAddressId', res.id);
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 400);
    });
  }
});
