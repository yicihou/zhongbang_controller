package com.zhongbang.controller.support.messaging;

/** Async messaging hook (pay.success, delayed close-order, etc.). */
public interface MessagePublisher {

    void publish(String routingKey, Object payload);
}
