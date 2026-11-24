const api = require('../../utils/api');
const fmt = require('../../utils/format');

const SORTS = [
  { label: '推荐', value: 'recommend' },
  { label: '距离最近', value: 'distance' },
  { label: '评分最高', value: 'rating' }
];

Page({
  data: {
    sorts: SORTS,
    sort: 'distance',
    list: [],
    selectedId: 0,
    selectedName: '',
    dateText: '',
    deposit: 0
  },

  onLoad(options) {
    this.skuId = options.skuId;
    this.date = options.date || fmt.dateAfter(3);
    this.slot = options.slot || 'AM';
    this.setData({
      dateText: fmt.shortDate(this.date) + ' ' + fmt.slotText(this.slot),
      deposit: Number(options.deposit || 0)
    });
    this.load();
  },

  load() {
    return api.getTechnicians({
      date: this.date,
      skuId: this.skuId,
      sort: this.data.sort
    }).then((list) => this.setData({ list: list || [] }));
  },

  onSort(e) {
    this.setData({ sort: e.currentTarget.dataset.sort }, () => this.load());
  },

  onPick(e) {
    const t = e.detail || {};
    if (!t.id) return;
    if (t.bookable === false) {
      wx.showToast({ title: '这天没有档期，请换师傅或改日期', icon: 'none' });
      return;
    }
    const slotOk = !(t.freeSlots || []).length || (t.freeSlots || []).some((s) => s.date === this.date && s.slot === this.slot);
    if (!slotOk) {
      wx.showToast({ title: '该时段已约满', icon: 'none' });
      return;
    }
    this.setData({ selectedId: t.id, selectedName: t.name });
  },

  confirm() {
    const t = this.data.list.filter((x) => x.id === this.data.selectedId)[0] || null;
    const ch = this.getOpenerEventChannel && this.getOpenerEventChannel();
    if (ch && ch.emit) ch.emit('selectTech', t);
    wx.navigateBack();
  }
});
