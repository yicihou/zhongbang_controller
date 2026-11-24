/**
 * 设备档案规则：只查当前登录客户的设备；绑定序列号时如果已被别人占用则拒绝。
 * 由 {@code controller.customer} 调用，通过 {@code mapper.customer} 读写 {@code customer_asset}。
 */
package com.zhongbang.controller.service.customer;
