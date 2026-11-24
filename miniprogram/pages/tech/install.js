const api = require('../../utils/api');
const upload = require('../../utils/upload');

const ITEMS = [
  { key: 'unpack', label: '拆箱核对配件' },
  { key: 'build', label: '组装并调平' },
  { key: 'test', label: '试缝正常' },
  { key: 'train', label: '已做操作培训' }
];

Page({
  data: {
    w: {},
    items: ITEMS.map((it) => Object.assign({ on: false }, it)),
    sn: '',
    images: [],
    remark: '',
    hasInk: false,
    submitting: false
  },

  onLoad(q) {
    this.id = q.id;
    api.getTechWorkorder(this.id).then((w) => this.setData({ w }));
  },

  onReady() {
    this.ctx = wx.createCanvasContext('sign', this);
    this.ctx.setStrokeStyle('#1A1E24');
    this.ctx.setLineWidth(3);
    this.ctx.setLineCap('round');
    this.ctx.setLineJoin('round');
  },

  toggle(e) {
    const key = e.currentTarget.dataset.key;
    const items = this.data.items.map((it) => (it.key === key ? Object.assign({}, it, { on: !it.on }) : it));
    this.setData({ items });
  },

  onSn(e) { this.setData({ sn: e.detail.value }); },
  onRemark(e) { this.setData({ remark: e.detail.value }); },

  scanSn() {
    wx.scanCode({
      success: (res) => this.setData({ sn: (res.result || '').trim() }),
      fail: () => wx.showToast({ title: '可以手动输入机身码', icon: 'none' })
    });
  },

  addImage() {
    upload.chooseImages(this.data.images, 9).then((images) => this.setData({ images }));
  },

  touchStart(e) {
    const t = e.touches[0];
    this.last = { x: t.x, y: t.y };
    this.drawing = true;
    this.setData({ hasInk: true });
  },

  touchMove(e) {
    if (!this.drawing || !this.ctx) return;
    const t = e.touches[0];
    this.ctx.moveTo(this.last.x, this.last.y);
    this.ctx.lineTo(t.x, t.y);
    this.ctx.stroke();
    this.ctx.draw(true);
    this.last = { x: t.x, y: t.y };
  },

  touchEnd() { this.drawing = false; },

  clearSign() {
    this.ctx = wx.createCanvasContext('sign', this);
    this.ctx.clearRect(0, 0, 1000, 400);
    this.ctx.draw();
    this.ctx.setStrokeStyle('#1A1E24');
    this.ctx.setLineWidth(3);
    this.ctx.setLineCap('round');
    this.setData({ hasInk: false });
  },

  exportSign() {
    return new Promise((resolve, reject) => {
      wx.canvasToTempFilePath({
        canvasId: 'sign',
        success: (r) => resolve(r.tempFilePath),
        fail: reject
      }, this);
    });
  },

  submit() {
    if (this.data.submitting) return;
    const checked = this.data.items.filter((it) => it.on).map((it) => it.label);
    if (checked.length < 3) return wx.showToast({ title: '请至少勾选 3 项安装内容', icon: 'none' });
    if (!this.data.sn.trim()) return wx.showToast({ title: '请填写机身码', icon: 'none' });
    if (this.data.images.length < 3) return wx.showToast({ title: '至少 3 张现场照片', icon: 'none' });
    if (!this.data.hasInk) return wx.showToast({ title: '请让客户在下方签字', icon: 'none' });
    this.setData({ submitting: true });
    this.exportSign().then((signImage) => upload.uploadOne(signImage, 'SIGN_IMAGE')).then((signImage) => {
      const remark = checked.join('、') + (this.data.remark ? '；' + this.data.remark : '');
      return api.finishWorkorder(this.id, {
        sn: this.data.sn.trim(),
        images: this.data.images,
        signImage,
        remark
      });
    }).then(() => {
      wx.showToast({ title: '安装单已提交', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 500);
    }).catch(() => this.setData({ submitting: false }));
  }
});
