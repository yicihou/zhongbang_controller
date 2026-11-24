const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { FAULTS } = require('../../utils/constants');

function emptyExternal() {
  return { brand: '', model: '', sn: '', faultDesc: '', purchaseChannel: '', purchaseProof: '', images: [] };
}

function emptyLine(mode) {
  return {
    key: 'L' + Date.now() + Math.floor(Math.random() * 1000),
    mode: mode || 'asset', // asset | external
    assetId: 0,
    assetLabel: '',
    warrantyText: '',
    faultType: 'THREAD_BREAK',
    faultDesc: '',
    images: [],
    external: emptyExternal()
  };
}

Page({
  data: {
    faults: FAULTS,
    assets: [],
    lines: [],
    date: '',
    minDate: '',
    slot: 'AM',
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
      const line = emptyLine('asset');
      const preset = assets.filter((a) => a.id === this.presetAssetId)[0] || assets[0];
      if (preset) {
        line.assetId = preset.id;
        line.assetLabel = preset.model + ' · ' + (preset.sn || '无 SN');
        line.warrantyText = preset.warrantyText || '';
      } else {
        line.mode = 'external';
      }
      this.setData({ assets, lines: [line] });
    });
  },

  addLine() {
    const lines = this.data.lines.concat([emptyLine(this.data.assets.length ? 'asset' : 'external')]);
    if (lines[lines.length - 1].mode === 'asset' && this.data.assets[0]) {
      const a = this.data.assets[0];
      lines[lines.length - 1].assetId = a.id;
      lines[lines.length - 1].assetLabel = a.model + ' · ' + (a.sn || '无 SN');
      lines[lines.length - 1].warrantyText = a.warrantyText || '';
    }
    this.setData({ lines });
  },

  removeLine(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    if (this.data.lines.length <= 1) return wx.showToast({ title: '至少保留一台', icon: 'none' });
    const lines = this.data.lines.slice();
    lines.splice(idx, 1);
    this.setData({ lines });
  },

  setMode(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const mode = e.currentTarget.dataset.mode;
    const lines = this.data.lines.slice();
    lines[idx].mode = mode;
    this.setData({ lines });
  },

  chooseAsset(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    if (!this.data.assets.length) return wx.showToast({ title: '暂无本店设备，请填外购', icon: 'none' });
    wx.showActionSheet({
      itemList: this.data.assets.map((a) => a.model + ' · ' + (a.sn || '无 SN')),
      success: (res) => {
        const a = this.data.assets[res.tapIndex];
        const lines = this.data.lines.slice();
        lines[idx].assetId = a.id;
        lines[idx].assetLabel = a.model + ' · ' + (a.sn || '无 SN');
        lines[idx].warrantyText = a.warrantyText || '';
        this.setData({ lines });
      }
    });
  },

  onFault(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const lines = this.data.lines.slice();
    lines[idx].faultType = e.currentTarget.dataset.code;
    this.setData({ lines });
  },

  onDesc(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const lines = this.data.lines.slice();
    lines[idx].faultDesc = e.detail.value;
    this.setData({ lines });
  },

  onExt(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const field = e.currentTarget.dataset.field;
    const lines = this.data.lines.slice();
    lines[idx].external[field] = e.detail.value;
    this.setData({ lines });
  },

  chooseImage(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const upload = require('../../utils/upload');
    const lines = this.data.lines.slice();
    const target = lines[idx].mode === 'external' ? lines[idx].external.images : lines[idx].images;
    upload.chooseImages(target, 9).then((images) => {
      if (lines[idx].mode === 'external') lines[idx].external.images = images;
      else lines[idx].images = images;
      this.setData({ lines });
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
    const linesOut = [];
    for (let i = 0; i < this.data.lines.length; i++) {
      const L = this.data.lines[i];
      if (L.mode === 'asset') {
        if (!L.assetId) return wx.showToast({ title: '请选择本店设备', icon: 'none' });
        if (!L.faultDesc.trim()) return wx.showToast({ title: '请填写故障描述', icon: 'none' });
        if (!L.images.length) return wx.showToast({ title: '请上传现场照片', icon: 'none' });
        linesOut.push({
          assetId: L.assetId,
          faultType: L.faultType,
          faultDesc: L.faultDesc,
          images: L.images
        });
      } else {
        const ext = L.external;
        if (!ext.brand.trim() || !ext.model.trim()) {
          return wx.showToast({ title: '外购须填品牌和型号', icon: 'none' });
        }
        const faultDesc = (ext.faultDesc || L.faultDesc || '').trim();
        if (!faultDesc) return wx.showToast({ title: '外购须填故障描述', icon: 'none' });
        if (!(ext.images || []).length) return wx.showToast({ title: '外购须上传现场照片', icon: 'none' });
        linesOut.push({
          external: {
            brand: ext.brand.trim(),
            model: ext.model.trim(),
            sn: (ext.sn || '').trim(),
            faultDesc,
            images: ext.images,
            purchaseChannel: (ext.purchaseChannel || '').trim(),
            purchaseProof: ext.purchaseProof || ''
          },
          faultType: L.faultType,
          faultDesc,
          images: ext.images
        });
      }
    }
    this.setData({ submitting: true });
    api.createRepair({
      expectDate: this.data.date,
      expectSlot: this.data.slot,
      lines: linesOut
    }).then((res) => {
      this.setData({ submitting: false });
      const id = res.ticketId || res.id || res.workOrderId;
      wx.redirectTo({ url: '/pages/repair/detail?id=' + id });
    }).catch(() => this.setData({ submitting: false }));
  }
});
