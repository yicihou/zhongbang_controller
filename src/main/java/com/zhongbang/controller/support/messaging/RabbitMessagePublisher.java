package com.zhongbang.controller.support.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitMessagePublisher implements MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(routingKey, payload);
    }
}
