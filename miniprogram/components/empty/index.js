Component({
  properties: {
    show: { type: Boolean, value: true },
    icon: { type: String, value: '□' },
    title: { type: String, value: '这里还是空的' },
    desc: { type: String, value: '' },
    action: { type: String, value: '' }
  },
  methods: {
    onAction() {
      this.triggerEvent('action');
    }
  }
});
