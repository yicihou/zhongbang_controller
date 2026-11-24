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

/**
 * 按 yml 开关注册幂等、锁、消息的实现。业务代码现在还没有注入这三类 Bean。
 *
 * <p>{@code @Configuration} 表示本类是「怎么装配对象」的说明，不是业务服务。
 * {@code @Bean} 标注的方法，返回值会被放进容器，别处按类型注入。同一种接口只应有一个 Bean，
 * 所以下面成对的方法用条件互斥。
 * {@code @ConditionalOnProperty} 看配置项：{@code zhongbang.redis.enabled=false} 或没写时用内存版；
 * 写成 true 才用 Redis。RabbitMQ 同理。
 *
 * <p>{@code dev} 配置排除了 Redis 和 Rabbit 的自动配置。若只把 enabled 改成 true、仍用 dev，
 * 容器会要 {@code StringRedisTemplate} 却创建不出来，启动失败。连真实中间件请用 {@code infra} 配置。
 */
@Configuration
public class InfrastructureAutoConfig {

    /** 默认路径。进程内 Map，重启即丢，多实例之间不共享。 */
    @Bean
    @ConditionalOnProperty(prefix = "zhongbang.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
    IdempotencyService inMemoryIdempotencyService() {
        return new InMemoryIdempotencyService();
    }

    /** 仅当 zhongbang.redis.enabled=true。键前缀 idem:，依赖已经配置好的 Redis。 */
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

    /** 默认。publish 只打 debug 日志，没有队列，也没有消费者。 */
    @Bean
    @ConditionalOnProperty(prefix = "zhongbang.rabbitmq", name = "enabled", havingValue = "false", matchIfMissing = true)
    MessagePublisher noOpMessagePublisher() {
        return new NoOpMessagePublisher();
    }

    /** 仅当 zhongbang.rabbitmq.enabled=true。发到默认交换机，routingKey 由调用方传入。本仓库还没有调用方。 */
    @Bean
    @ConditionalOnProperty(prefix = "zhongbang.rabbitmq", name = "enabled", havingValue = "true")
    MessagePublisher rabbitMessagePublisher(RabbitTemplate rabbitTemplate) {
        return new RabbitMessagePublisher(rabbitTemplate);
    }
}
