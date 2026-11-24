/**
 * 页面可见时大约每 10 秒问一次 GET /api/me/changes。
 * 只有订单、报修或商品真的变了，才再去拉列表或详情，避免每 10 秒都传整页数据。
 * 页面 onHide、onUnload 时要 stop，否则切走以后定时器还在跑。
 *
 * 用法：
 *   onShow 里先 load()，再 refresh.start(this, { areas: ['orders'], load: (quiet) => this.load(quiet) })
 *   onHide / onUnload 里 refresh.stop(this)
 * areas 用 changes 返回的字段名：orders、repairs、products。
 */
const api = require('./api');

const INTERVAL = 10000;

function start(page, options) {
  stop(page);
  page._changeAreas = (options && options.areas) || [];
  page._changeLoad = options && options.load;
  // 先记下当前时间。这次不刷新，因为调用方刚刚已经 load 过。
  api.changes().then((data) => {
    page._changeCursor = data && data.cursor;
  }).catch(() => {});
  page._changeTimer = setInterval(() => tick(page), INTERVAL);
}

function tick(page) {
  const load = page._changeLoad;
  api.changes(page._changeCursor).then((data) => {
    const prev = page._changeCursor;
    page._changeCursor = data && data.cursor;
    if (!prev || !data || !data.changed) return;
    const areas = page._changeAreas || [];
    const hit = areas.some((name) => data[name]);
    if (hit && typeof load === 'function') load(true);
  }).catch(() => {
    // 变更接口暂时不可用时，退回到直接重拉，保证管理端的修改还能出现。
    if (typeof load === 'function') load(true);
  });
}

function stop(page) {
  if (page && page._changeTimer) {
    clearInterval(page._changeTimer);
    page._changeTimer = null;
  }
}

module.exports = { start, stop, INTERVAL };
