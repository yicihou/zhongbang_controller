const api = require('../../utils/api');
const upload = require('../../utils/upload');

const TAGS = ['准时上门', '讲解清楚', '安装规范', '态度好', '还需改进'];

Page({
  data: {
    score: 5,
    stars: [1, 2, 3, 4, 5],
    tags: TAGS.map((name) => ({ name, on: name === '准时上门' || name === '讲解清楚' })),
    content: '',
    images: [],
    submitting: false,
    target: ''
  },

  onLoad(q) {
    this.orderId = Number(q.orderId || q.id || 0);
    this.workOrderId = Number(q.workOrderId || 0);
    this.setData({ target: q.title ? decodeURIComponent(q.title) : '本次服务' });
  },

  onStar(e) {
    this.setData({ score: Number(e.currentTarget.dataset.n) });
  },

  onTag(e) {
    const tag = e.currentTarget.dataset.tag;
    const tags = this.data.tags.map((t) => (t.name === tag ? { name: t.name, on: !t.on } : t));
    this.setData({ tags });
  },

  onContent(e) {
    this.setData({ content: e.detail.value });
  },

  addImage() {
    upload.chooseImages(this.data.images, 6).then((images) => this.setData({ images }));
  },

  submit() {
    if (this.data.submitting) return;
    if (!this.data.score) {
      wx.showToast({ title: '请先打分', icon: 'none' });
      return;
    }
    this.setData({ submitting: true });
    api.createEvaluation({
      orderId: this.orderId || undefined,
      workOrderId: this.workOrderId || undefined,
      score: this.data.score,
      tags: this.data.tags.filter((t) => t.on).map((t) => t.name),
      content: this.data.content,
      images: this.data.images
    }).then(() => {
      wx.showToast({ title: '感谢评价', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 500);
    }).catch(() => this.setData({ submitting: false }));
  }
});
