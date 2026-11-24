/**
 * 支付占位包。
 *
 * <p>表 {@code payment} 已建好，默认渠道写成了微信支付字段，但没有下单、回调或验签代码。
 * 安全配置把 {@code /api/pay/notify} 放行了，目前没有对应的 Controller。
 */
package com.zhongbang.controller.module.payment;
