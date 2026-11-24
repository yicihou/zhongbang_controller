const api = require('./api');
const config = require('../config');

/** 选图，最多 max 张，返回合并后的本地路径列表 */
function chooseImages(existed, max) {
  const list = existed || [];
  const remain = max - list.length;
  if (remain <= 0) {
    wx.showToast({ title: '最多 ' + max + ' 张', icon: 'none' });
    return Promise.resolve(list);
  }
  return new Promise((resolve) => {
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const paths = (res.tempFiles || []).map((f) => f.tempFilePath);
        resolve(list.concat(paths));
      },
      fail: () => resolve(list)
    });
  });
}

/** 选一段不超过 30 秒的视频，取消时返回空字符串 */
function chooseVideo() {
  return new Promise((resolve) => {
    wx.chooseMedia({
      count: 1,
      mediaType: ['video'],
      sourceType: ['album', 'camera'],
      maxDuration: 30,
      success: (res) => {
        const f = (res.tempFiles || [])[0];
        if (!f) return resolve('');
        if (f.duration && f.duration > 30) {
          wx.showToast({ title: '视频请控制在 30 秒内', icon: 'none' });
          return resolve('');
        }
        resolve(f.tempFilePath);
      },
      fail: () => resolve('')
    });
  });
}

/**
 * 上传单文件。
 * USE_MOCK 或拿不到可用 OSS host 时，直接回传本地临时路径，方便离线把流程跑通。
 * 真环境走 GET /api/upload/policy + wx.uploadFile 直传。
 */
function uploadOne(filePath, bizType) {
  if (!filePath) return Promise.resolve('');
  if (config.USE_MOCK || filePath.indexOf('http') === 0) return Promise.resolve(filePath);
  return api.getUploadPolicy(bizType || 'WORKORDER_PHOTO').then((policy) => {
    if (!policy || !policy.host || policy.policy === 'mock' || policy.policy === 'mock-policy') {
      return filePath;
    }
    const key = (policy.dir || '') + Date.now() + '.jpg';
    return new Promise((resolve, reject) => {
      wx.uploadFile({
        url: policy.host,
        filePath: filePath,
        name: 'file',
        formData: {
          key: key,
          policy: policy.policy,
          OSSAccessKeyId: policy.accessKeyId,
          signature: policy.signature,
          success_action_status: '200'
        },
        success: (res) => {
          if (res.statusCode >= 200 && res.statusCode < 300) resolve(policy.host.replace(/\/$/, '') + '/' + key);
          else reject(res);
        },
        fail: reject
      });
    });
  }).catch(() => filePath);
}

module.exports = { chooseImages, chooseVideo, uploadOne };
