const api = require('./utils/api');

App({
  globalData: {
    token: '',
    customer: null,
    loginReady: null
  },

  onLaunch() {
    this.globalData.token = wx.getStorageSync('token') || '';
    // 静默登录：失败不阻断页面，页面各自兜底
    this.globalData.loginReady = this.login().catch(() => null);
  },

  /** 微信登录换 token，返回 Promise */
  login() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: (res) => {
          if (!res.code) return reject(new Error('wx.login 未返回 code'));
          api.login(res.code).then((data) => {
            this.globalData.token = data.token;
            this.globalData.customer = data.customer;
            wx.setStorageSync('token', data.token);
            resolve(data);
          }).catch(reject);
        },
        fail: reject
      });
    });
  },

  /** 业务方需要确保登录后再请求 */
  ensureLogin() {
    if (this.globalData.token) return Promise.resolve();
    if (this.globalData.loginReady) return this.globalData.loginReady;
    this.globalData.loginReady = this.login();
    return this.globalData.loginReady;
  }
});
