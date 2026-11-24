const api = require('../../utils/api');
const upload = require('../../utils/upload');

Page({
  data: {
    w: {},
    remark: '',
    result: '试机正常',
    images: [],
    partName: '',
    partQty: '1',
    parts: [],
    oldPart: 'CUSTOMER',
    blocked: false,
    submitting: false
  },

  onLoad(q) {
    this.id = q.id;
    api.getTechWorkorder(this.id).then((w) => {
      const blocked = w.type === 'REPAIR' && !w.inWarranty && !w.quoteConfirmed;
      this.setData({ w, blocked });
    });
  },

  onRemark(e) { this.setData({ remark: e.detail.value }); },
  onResult(e) { this.setData({ result: e.detail.value }); },
  onPartName(e) { this.setData({ partName: e.detail.value }); },
  onPartQty(e) { this.setData({ partQty: e.detail.value }); },
  onOld(e) { this.setData({ oldPart: e.currentTarget.dataset.v }); },

  addPart() {
    const name = (this.data.partName || '').trim();
    if (!name) return wx.showToast({ title: '填写配件名称', icon: 'none' });
    const qty = Number(this.data.partQty || 1);
    const parts = this.data.parts.concat([{ name, qty, skuId: 0, price: 0 }]);
    this.setData({ parts, partName: '' });
  },

  removePart(e) {
    const parts = this.data.parts.slice();
    parts.splice(e.currentTarget.dataset.i, 1);
    this.setData({ parts });
  },

  addImage() {
    upload.chooseImages(this.data.images, 9).then((images) => this.setData({ images }));
  },

  submit() {
    if (this.data.blocked) {
      wx.showToast({ title: '客户还未确认报价', icon: 'none' });
      return;
    }
    if (!this.data.remark.trim()) return wx.showToast({ title: '请填写处理措施', icon: 'none' });
    this.setData({ submitting: true });
    const remark = this.data.remark.trim() + '；试机：' + (this.data.result || '未填');
    api.finishWorkorder(this.id, {
      remark,
      images: this.data.images,
      parts: this.data.parts,
      oldPartHandling: this.data.oldPart
    }).then(() => {
      wx.showToast({ title: '维修单已提交', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 500);
    }).catch(() => this.setData({ submitting: false }));
  }
});
