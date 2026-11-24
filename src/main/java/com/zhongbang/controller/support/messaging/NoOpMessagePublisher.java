package com.zhongbang.controller.support.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 什么都不发送，只在 debug 级别记一条日志。RabbitMQ 关闭时使用，调用方不会得到异常。
 */
public class NoOpMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpMessagePublisher.class);

    @Override
    public void publish(String routingKey, Object payload) {
        log.debug("No-op publish routingKey={} payload={}", routingKey, payload);
    }
}
