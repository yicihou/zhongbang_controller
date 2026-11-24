const api = require('../../utils/api');
const fmt = require('../../utils/format');
const { pay } = require('../../utils/pay');

const DESC = {
  UNPAID: '请在 30 分钟内完成定金支付，超时订单将自动关闭',
  DEPOSIT_PAID: '已收到定金，等待师傅上门安装',
  INSTALLING: '师傅已上门，安装完成后请验收',
  PENDING_BALANCE: '安装已完成，请支付尾款',
  COMPLETED: '订单已完成，感谢使用',
  CANCELLED: '订单已取消',
  REFUNDING: '退款处理中',
  REFUNDED: '退款已完成'
};

Page({
  data: {
    o: { address: {}, items: [], timeline: [] },
    statusDesc: '',
    slotText: '',
    techInitial: '',
    hasAction: false,
    payType: '',
    payText: '',
    canCancel: false,
    canCall: false,
    canAccept: false,
    canRepair: false,
    canEvaluate: false
  },

  onLoad(options) {
    this.id = options.id;
    if (options.paid) {
      wx.showToast({ title: '支付成功', icon: 'success' });
    }
  },

  onShow() {
    this.load();
  },

  load() {
    return api.getOrder(this.id).then((o) => {
      const actions = o.actions || [];
      let payType = '';
      if (actions.indexOf('PAY_DEPOSIT') >= 0) payType = 'DEPOSIT';
      if (actions.indexOf('PAY_BALANCE') >= 0) payType = 'BALANCE';

      this.setData({
        o: Object.assign({}, o, {
          totalText: fmt.yuan(o.totalAmount),
          paidText: fmt.yuan(o.paidAmount),
          balanceText: fmt.yuan(o.balanceAmount),
          items: (o.items || []).map((it) => Object.assign({}, it, { priceText: fmt.yuan(it.price) }))
        }),
        statusDesc: DESC[o.status] || '',
        slotText: fmt.slotText(o.installSlot),
        techInitial: o.technician ? (o.technician.name || '师').slice(0, 1) : '',
        payType,
        payText: payType === 'DEPOSIT'
          ? '支付定金 ¥' + fmt.yuan(o.depositAmount)
          : '支付尾款 ¥' + fmt.yuan(o.balanceAmount),
        canCancel: actions.indexOf('CANCEL') >= 0,
        canCall: actions.indexOf('CALL_TECHNICIAN') >= 0,
        canAccept: o.status === 'INSTALLING',
        canRepair: actions.indexOf('REPAIR') >= 0,
        canEvaluate: actions.indexOf('EVALUATE') >= 0,
        hasAction: actions.length > 0 || o.status === 'INSTALLING'
      });
    });
  },

  pay() {
    const type = this.data.payType;
    pay(this.id, type).then(() => {
      wx.navigateTo({ url: '/pages/order/result?orderId=' + this.id + '&type=' + type });
    }).catch((e) => {
      if (e && e.msg && !e.code) wx.showToast({ title: e.msg, icon: 'none' });
    });
  },

  cancel() {
    wx.showModal({
      title: '取消订单',
      content: '取消后库存与师傅档期会被释放，确定取消？',
      success: (res) => {
        if (!res.confirm) return;
        api.cancelOrder(this.id, '用户主动取消').then(() => {
          wx.showToast({ title: '已取消', icon: 'success' });
          this.load();
        });
      }
    });
  },

  accept() {
    wx.navigateTo({ url: '/pages/order/accept?id=' + this.id });
  },

  callTech() {
    const t = this.data.o.technician;
    if (!t || !t.mobile) return;
    wx.makePhoneCall({ phoneNumber: String(t.mobile).replace(/\*/g, '') });
  },

  goRepair() {
    wx.navigateTo({ url: '/pages/repair/create' });
  },

  goEvaluate() {
    const name = (this.data.o.items && this.data.o.items[0] && this.data.o.items[0].name) || '安装服务';
    wx.navigateTo({
      url: '/pages/order/evaluate?orderId=' + this.id + '&title=' + encodeURIComponent(name)
    });
  }
});
