const api = require('../../utils/api');
const fmt = require('../../utils/format');

Page({
  data: {
    list: [],
    selectedId: 0,
    selectedName: '',
    dateText: ''
  },

  onLoad(options) {
    this.skuId = options.skuId;
    this.date = options.date || fmt.dateAfter(3);
    this.setData({ dateText: fmt.shortDate(this.date) + ' ' });
    this.load();
  },

  load() {
    return api.getTechnicians({ date: this.date, skuId: this.skuId }).then((list) => {
      this.setData({
        list: (list || []).map((t) => Object.assign({}, t, {
          initial: (t.name || '师').slice(0, 1),
          skillText: (t.skills || []).join(' / ')
        }))
      });
    });
  },

  onPick(e) {
    const id = Number(e.currentTarget.dataset.id);
    const t = this.data.list.filter((x) => x.id === id)[0];
    if (!t) return;
    this.setData({ selectedId: id, selectedName: t.name });
  },

  confirm() {
    const t = this.data.list.filter((x) => x.id === this.data.selectedId)[0] || null;
    const ch = this.getOpenerEventChannel && this.getOpenerEventChannel();
    if (ch) ch.emit('selectTech', t);
    wx.navigateBack();
  }
});
