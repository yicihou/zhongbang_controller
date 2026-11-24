/**
 * 支付的业务规则占位，目前没有 Service。
 * 以后的规则放在本包，由同名 Controller 调用，通过同名 Mapper 落库。
 * 需要拆成接口和实现时，实现类放在本包下的 {@code impl}。
 *
 * <p>表 {@code payment} 已建好，默认渠道写成了微信支付字段，但没有下单、回调或验签代码。
 * 安全配置把 {@code /api/pay/notify} 放行了，目前没有对应的 Controller。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.payment}、{@code service.payment}、{@code mapper.payment}、{@code pojo.payment}。
 */
package com.zhongbang.controller.service.payment;
