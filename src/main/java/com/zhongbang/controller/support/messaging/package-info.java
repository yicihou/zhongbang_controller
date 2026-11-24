/**
 * 异步消息占位。打算在支付成功、延迟关单时发消息。
 * RabbitMQ 关闭时 {@code publish} 只打一条 debug 日志，消息不会进队列，也没有消费者。
 */
package com.zhongbang.controller.support.messaging;
