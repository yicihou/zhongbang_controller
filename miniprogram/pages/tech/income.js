const api = require('../../utils/api');
const fmt = require('../../utils/format');

/** 契约没有结算接口（FR-G07 为 P2）。这里用已完成工单在本地做演示汇总。 */
const RATE = { INSTALL: 8000, REPAIR: 5000 };

Page({
  data: {
    done: 0,
    install: 0,
    repair: 0,
    pending: 0,
    cents: 0,
    rows: []
  },

  onShow() {
    api.getTechWorkorders({}).then((d) => {
      const list = (d && d.list) || [];
      const doneList = list.filter((w) => w.status === 'DONE');
      const rows = doneList.map((w) => {
        const cents = RATE[w.type] || 5000;
        return {
          id: w.id,
          title: (w.typeText || '') + ' ' + (w.workNo || ''),
          cents,
          text: fmt.yuan(cents)
        };
      });
      const cents = rows.reduce((s, r) => s + r.cents, 0);
      this.setData({
        done: doneList.length,
        install: doneList.filter((w) => w.type === 'INSTALL').length,
        repair: doneList.filter((w) => w.type === 'REPAIR').length,
        pending: list.filter((w) => w.status === 'PENDING' || w.status === 'ACCEPTED' || w.status === 'ON_SITE').length,
        cents,
        rows
      });
    });
  }
});
