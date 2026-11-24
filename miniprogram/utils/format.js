/** 分 → 元，带千分位；整数金额不显示 .00 */
function yuan(cents) {
  const v = Number(cents || 0) / 100;
  const s = v.toFixed(2).replace(/\.00$/, '');
  const parts = s.split('.');
  const int = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return parts[1] ? int + '.' + parts[1] : int;
}

/** 分 → 元，仅两位小数（用于对账、明细） */
function yuan2(cents) {
  return (Number(cents || 0) / 100).toFixed(2);
}

function pad(n) {
  return n < 10 ? '0' + n : '' + n;
}

/** n 天后日期 yyyy-MM-dd */
function dateAfter(n) {
  const d = new Date();
  d.setDate(d.getDate() + (n || 0));
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}

/** 2025-11-28 → 11-28 周四 */
function shortDate(str) {
  if (!str) return '';
  const d = new Date(str.replace(/-/g, '/'));
  if (isNaN(d.getTime())) return str;
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()];
  return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + week;
}

function slotText(slot) {
  return slot === 'PM' ? '下午' : '上午';
}

module.exports = { yuan, yuan2, dateAfter, shortDate, slotText, pad };
