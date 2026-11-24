const api = require('../../utils/api');
const fmt = require('../../utils/format');
const config = require('../../config');

const DESC = {
  UNPAID: '请在 30 分钟内完成定金支付，超时订单将自动关闭',
  DEPOSIT_PAID: '已收到定金，等待师傅上门安装',
  INSTALLING: '师傅已上门，安装完成后请验收',
  PENDING_BALANCE: '安装已完成，请支付尾款',
  COMPLETED: '订单已完成，感谢使用',
  CANCELLED: '订单已取消',
  REFUNDING: '退款处理中',
  REFUNDED: '退款已完成',
  PENDING_PAYMENT: '订单已提交。安装费如显示待定，由门店确认后再收款。请按商家指引线下付款。',
  PARTIALLY_PAID: '已收到一部分货款，剩余金额请继续线下支付。',
  PAID: '货款已结清，等待发货或安装。',
  FULFILLING: '商家已发货或已派安装师傅。'
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
    canRepair: false
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
        hasAction: actions.length > 0 || o.status === 'INSTALLING',
        offlineHint: !config.USE_MOCK && o.balanceAmount > 0 && o.status !== 'CANCELLED'
      });
    });
  },

  pay() {
    const type = this.data.payType;
    api.prepay(this.id, type).then((p) => {
      const finish = () => {
        wx.showToast({ title: '支付成功', icon: 'success' });
        this.load();
      };
      if (config.USE_MOCK) {
        return api.mockPaySuccess(this.id, type).then(finish);
      }
      wx.requestPayment({
        timeStamp: p.payParams.timeStamp,
        nonceStr: p.payParams.nonceStr,
        package: p.payParams.package,
        signType: p.payParams.signType,
        paySign: p.payParams.paySign,
        success: finish,
        fail: () => wx.showToast({ title: '已取消支付', icon: 'none' })
      });
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
    wx.showModal({
      title: '确认验收',
      content: '确认机器安装调试正常？确认后将生成尾款单。',
      success: (res) => {
        if (!res.confirm) return;
        api.acceptOrder(this.id, true, '客户确认验收').then(() => {
          wx.showToast({ title: '验收成功', icon: 'success' });
          this.load();
        });
      }
    });
  },

  callTech() {
    const t = this.data.o.technician;
    if (!t) return;
    wx.makePhoneCall({ phoneNumber: (t.mobile || '').replace(/\*/g, '0') });
  },

  goRepair() {
    wx.navigateTo({ url: '/pages/repair/create' });
  }
});
