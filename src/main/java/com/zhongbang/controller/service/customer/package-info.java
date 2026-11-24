/**
 * 客户侧规则。{@code AssetService} 只查当前登录客户的设备，序列号已被别人占用时拒绝绑定。
 * {@code ChangeQueryService} 回答「自从 since 之后，订单、报修、商品有没有变过」，给小程序轮询用。
 */
package com.zhongbang.controller.service.customer;
