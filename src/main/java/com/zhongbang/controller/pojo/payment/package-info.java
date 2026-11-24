/**
 * 支付的数据对象占位，目前没有实体、请求体或返回体。
 * 以后按用途再分 {@code entity}（对应表）、{@code dto}（请求）、{@code vo}（返回给页面的字段）。
 * 这些类不写 SQL。
 *
 * <p>表 {@code payment} 已建好，默认渠道写成了微信支付字段，但没有下单、回调或验签代码。
 * 安全配置把 {@code /api/pay/notify} 放行了，目前没有对应的 Controller。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.payment}、{@code service.payment}、{@code mapper.payment}、{@code pojo.payment}。
 */
package com.zhongbang.controller.pojo.payment;
