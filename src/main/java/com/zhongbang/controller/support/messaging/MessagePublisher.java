package com.zhongbang.controller.support.messaging;

/**
 * 发一条异步消息的钩子，打算用于支付成功、延迟关单。没有调用方，也没有监听者。
 */
public interface MessagePublisher {

    /**
     * @param routingKey 路由键，例如将来的 pay.success。Rabbit 版把它交给默认交换机
     * @param payload 消息体，由 RabbitTemplate 按配置序列化
     */
    void publish(String routingKey, Object payload);
}
