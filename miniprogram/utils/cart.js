/**
 * 小程序购物车，存在本地。USE_MOCK 为 true 时确认页仍走原来的单行下单，不读这里。
 * 真实后端的下单接口一次可以收多行 items。
 */
const KEY = 'zb_cart';

function read() {
  try {
    const list = wx.getStorageSync(KEY);
    return Array.isArray(list) ? list : [];
  } catch (e) {
    return [];
  }
}

function write(list) {
  wx.setStorageSync(KEY, list);
  return list;
}

function add(item) {
  const list = read();
  const hit = list.filter((row) => row.skuId === item.skuId && row.deliveryMethod === item.deliveryMethod)[0];
  if (hit) {
    hit.quantity = Number(hit.quantity || 1) + Number(item.quantity || 1);
  } else {
    list.push({
      skuId: item.skuId,
      skuName: item.skuName || '',
      productName: item.productName || '',
      price: Number(item.price || 0),
      coverImage: item.coverImage || '',
      quantity: Number(item.quantity || 1),
      deliveryMethod: item.deliveryMethod || 'EXPRESS'
    });
  }
  return write(list);
}

module.exports = {
  read,
  write,
  add,
  clear: () => write([]),
  count: () => read().reduce((sum, row) => sum + Number(row.quantity || 0), 0)
};
