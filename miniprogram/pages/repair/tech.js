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
    sort: 'recommend',
    list: [],
    selectedId: 0,
    selectedName: '',
    dateText: '',
    outWarranty: false
  },

  onLoad(q) {
    this.date = q.date || fmt.dateAfter(2);
    this.setData({
      dateText: fmt.shortDate(this.date),
      outWarranty: q.warranty === 'OUT'
    });
    this.load();
  },

  load() {
    return api.getTechnicians({ date: this.date, sort: this.data.sort }).then((list) => {
      this.setData({ list: list || [] });
    });
  },

  onSort(e) {
    this.setData({ sort: e.currentTarget.dataset.sort }, () => this.load());
  },

  onPick(e) {
    const t = e.detail || {};
    if (t.bookable === false) {
      wx.showToast({ title: '这天没有档期，换一天或换师傅', icon: 'none' });
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
