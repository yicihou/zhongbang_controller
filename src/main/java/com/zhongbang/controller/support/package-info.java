/**
 * 横切能力：幂等、分布式锁、消息发布。给以后的下单和支付回调用。
 *
 * <p>三个接口都会按配置注册成 Bean，但现在没有任何业务 Service 调用它们。
 * 关掉 Redis / RabbitMQ 时用内存或空实现，应用仍能启动。
 */
package com.zhongbang.controller.support;
