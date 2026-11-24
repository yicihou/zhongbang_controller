const fmt = require('../../utils/format');

Component({
  properties: {
    item: { type: Object, value: {} },
    selected: { type: Boolean, value: false }
  },
  data: { star: '', initial: '师', slots: [] },
  observers: {
    item(t) {
      this.paint(t);
    }
  },
  lifetimes: {
    attached() {
      this.paint(this.data.item);
    }
  },
  methods: {
    paint(t) {
      t = t || {};
      const slots = (t.slots && t.slots.length)
        ? t.slots
        : (t.freeSlots || []).map((s) => ({ text: s.text, free: true }));
      this.setData({
        star: fmt.stars(t.rating),
        initial: (t.name || '师').slice(0, 1),
        skillText: (t.skills || []).join(' / '),
        slots: slots
      });
    },
    onTap() {
      this.triggerEvent('pick', this.data.item);
    }
  }
});
