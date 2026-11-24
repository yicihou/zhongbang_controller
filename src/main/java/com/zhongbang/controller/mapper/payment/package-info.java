/**
 * 支付的表访问占位，目前没有 Mapper。
 * 以后的接口放在本包并标 {@code @Mapper}，启动类的扫描范围已经包含这里。
 *
 * <p>表 {@code payment} 已建好，默认渠道写成了微信支付字段，但没有下单、回调或验签代码。
 * 安全配置把 {@code /api/pay/notify} 放行了，目前没有对应的 Controller。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.payment}、{@code service.payment}、{@code mapper.payment}、{@code pojo.payment}。
 */
package com.zhongbang.controller.mapper.payment;
