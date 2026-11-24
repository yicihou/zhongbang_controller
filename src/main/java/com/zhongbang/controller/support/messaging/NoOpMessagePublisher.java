package com.zhongbang.controller.support.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpMessagePublisher.class);

    @Override
    public void publish(String routingKey, Object payload) {
        log.debug("No-op publish routingKey={} payload={}", routingKey, payload);
    }
}
