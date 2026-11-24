const api = require('../../utils/api');
const fmt = require('../../utils/format');

const FAULTS = [
  { code: 'THREAD_BREAK', label: '断线 / 跳针' },
  { code: 'NOISE', label: '噪音异响' },
  { code: 'OIL', label: '不上油' },
  { code: 'ELECTRIC', label: '电控故障' },
  { code: 'FEED', label: '送布异常' },
  { code: 'OTHER', label: '其他' }
];

Page({
  data: {
    faults: FAULTS,
    assets: [],
    asset: {},
    activeFault: 'THREAD_BREAK',
    desc: '',
    images: [],
    date: '',
    minDate: '',
    slot: 'AM',
    tech: null,
    video: '',
    result: null,
    submitting: false
  },

  onLoad(q) {
    this.presetAssetId = Number(q.assetId || 0);
    this.setData({
      date: fmt.dateAfter(2),
      minDate: fmt.dateAfter(1)
    });
    this.loadAssets();
  },

  loadAssets() {
    return api.getAssets().then((list) => {
      const assets = list || [];
      const preset = assets.filter((a) => a.id === this.presetAssetId)[0];
      this.setData({ assets, asset: preset || assets[0] || {} });
    });
  },

  chooseAsset() {
    if (!this.data.assets.length) return;
    wx.showActionSheet({
      itemList: this.data.assets.map((a) => a.model + ' · ' + a.sn),
      success: (res) => this.setData({ asset: this.data.assets[res.tapIndex] })
    });
  },

  onFault(e) {
    this.setData({ activeFault: e.currentTarget.dataset.code });
  },

  onDesc(e) {
    this.setData({ desc: e.detail.value });
  },

  chooseImage() {
    const upload = require('../../utils/upload');
    upload.chooseImages(this.data.images, 9).then((images) => this.setData({ images }));
  },

  chooseVideo() {
    const upload = require('../../utils/upload');
    upload.chooseVideo().then((video) => {
      if (video) this.setData({ video });
    });
  },

  chooseTech() {
    const warranty = (this.data.asset.warrantyStatus === 'IN') ? 'IN' : 'OUT';
    wx.navigateTo({
      url: '/pages/repair/tech?date=' + this.data.date + '&warranty=' + warranty,
      events: {
        selectTech: (t) => this.setData({ tech: t })
      }
    });
  },

  onDate(e) {
    this.setData({ date: e.detail.value });
  },

  onSlot(e) {
    this.setData({ slot: e.currentTarget.dataset.slot });
  },

  submit() {
    if (this.data.submitting) return;
    if (!this.data.asset.id) return wx.showToast({ title: '请先选择设备', icon: 'none' });
    if (!this.data.desc.trim()) return wx.showToast({ title: '请描述一下故障现象', icon: 'none' });

    this.setData({ submitting: true });
    api.createRepair({
      assetId: this.data.asset.id,
      faultType: this.data.activeFault,
      faultDesc: this.data.desc,
      images: this.data.images,
      expectDate: this.data.date,
      expectSlot: this.data.slot,
      technicianId: this.data.tech ? this.data.tech.id : null,
      video: this.data.video
    }).then((res) => {
      this.setData({ submitting: false });
      wx.redirectTo({ url: '/pages/repair/detail?id=' + res.workOrderId });
    }).catch(() => this.setData({ submitting: false }));
  },

  goOrders() {
    wx.switchTab({ url: '/pages/order/list' });
  }
});
