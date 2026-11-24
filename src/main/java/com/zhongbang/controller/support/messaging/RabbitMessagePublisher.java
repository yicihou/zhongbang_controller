package com.zhongbang.controller.support.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * 把消息交给 Spring 的 {@code RabbitTemplate}，发到默认交换机，routingKey 原样使用。
 * 没有在代码里声明队列或绑定；交换机上没有匹配队列时，消息会被 RabbitMQ 丢掉。
 * 仅 {@code zhongbang.rabbitmq.enabled=true} 时注册。
 */
public class RabbitMessagePublisher implements MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    /** 不声明队列。调用前需要运维侧已经建好对应绑定，否则消息没有去处。 */
    public void publish(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(routingKey, payload);
    }
}
