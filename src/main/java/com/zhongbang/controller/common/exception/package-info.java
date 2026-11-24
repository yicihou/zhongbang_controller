/**
 * 业务异常和全局兜底。
 *
 * <p>Service 发现「参数不合法、没登录、设备已被别人绑定」时抛 {@code BusinessException}，
 * 不要自己拼 HTTP 状态码。{@code GlobalExceptionHandler} 负责把异常变成统一 JSON。
 * 没登录、没权限还有一套在 {@code security} 包里，两处返回的字段是对齐的。
 */
package com.zhongbang.controller.common.exception;
