/**
 * HTTP 响应的外形。
 *
 * <p>成功和失败都是 <code>{code, msg, data, traceId}</code>，{@code code == 0} 表示成功。
 * 分页是 <code>{list, total, page, size}</code>，{@code page} 从 1 开始，不用 0。
 */
package com.zhongbang.controller.common.api;
