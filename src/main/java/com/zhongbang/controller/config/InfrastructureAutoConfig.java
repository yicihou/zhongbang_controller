package com.zhongbang.controller.config;

import com.zhongbang.controller.support.idempotency.IdempotencyService;
import com.zhongbang.controller.support.idempotency.InMemoryIdempotencyService;
import com.zhongbang.controller.support.idempotency.RedisIdempotencyService;
import com.zhongbang.controller.support.lock.DistributedLock;
import com.zhongbang.controller.support.lock.InMemoryDistributedLock;
import com.zhongbang.controller.support.lock.RedisDistributedLock;
import com.zhongbang.controller.support.messaging.MessagePublisher;
import com.zhongbang.controller.support.messaging.NoOpMessagePublisher;
import com.zhongbang.controller.support.messaging.RabbitMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class InfrastructureAutoConfig {

    @Bean
    @ConditionalOnProperty(prefix = "zhongbang.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
    IdempotencyService inMemoryIdempotencyService() {
        return new InMemoryIdempotencyService();
    }

    @Bean
    @ConditionalOnProperty(prefix = "zhongbang.redis", name = "enabled", havingValue = "true")
    IdempotencyService redisIdempotencyService(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyService(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "zhongbang.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
    DistributedLock inMemoryDistributedLock() {
        return new InMemoryDistributedLock();
    }

    @Bean
    @ConditionalOnProperty(prefix = "zhongbang.redis", name = "enabled", havingValue = "true")
    DistributedLock redisDistributedLock(StringRedisTemplate redisTemplate) {
        return new RedisDistributedLock(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "zhongbang.rabbitmq", name = "enabled", havingValue = "false", matchIfMissing = true)
    MessagePublisher noOpMessagePublisher() {
        return new NoOpMessagePublisher();
    }

    @Bean
    @ConditionalOnProperty(prefix = "zhongbang.rabbitmq", name = "enabled", havingValue = "true")
    MessagePublisher rabbitMessagePublisher(RabbitTemplate rabbitTemplate) {
        return new RabbitMessagePublisher(rabbitTemplate);
    }
}
