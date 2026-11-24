const fmt = require('../../utils/format');

Component({
  properties: {
    cents: { type: Number, value: 0 },
    size: { type: String, value: '' }
  },
  data: { text: '0' },
  observers: {
    cents(v) {
      this.setData({ text: fmt.yuan(v) });
    }
  },
  lifetimes: {
    attached() {
      this.setData({ text: fmt.yuan(this.data.cents) });
    }
  }
});
