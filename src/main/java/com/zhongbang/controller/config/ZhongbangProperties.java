package com.zhongbang.controller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@code application.yml} 里 {@code zhongbang} 开头的配置绑到 Java 字段。
 * {@code @ConfigurationProperties} 本身不会生效，要在某个 {@code @Configuration} 上
 * {@code @EnableConfigurationProperties}。跨域那份由 {@code WebConfig} 打开。
 *
 * <p>真正决定用内存还是 Redis 的，是 {@code InfrastructureAutoConfig} 上的
 * {@code @ConditionalOnProperty}，它直接读 yml，不读下面的 {@code redis} / {@code rabbitmq} 字段。
 * 这两个字段以及 {@code h2Console} 目前没有任何业务代码调用。
 * {@code h2-console.enabled} 改成 true 也不会打开控制台：{@code SecurityConfig} 始终拒绝该路径，
 * 且 dev 里 Spring 自带的 H2 控制台也是关闭的。
 */
@ConfigurationProperties(prefix = "zhongbang")
public class ZhongbangProperties {

    private final Cors cors = new Cors();
    private final Redis redis = new Redis();
    private final Rabbitmq rabbitmq = new Rabbitmq();
    private final H2Console h2Console = new H2Console();

    public Cors getCors() {
        return cors;
    }

    public Redis getRedis() {
        return redis;
    }

    public Rabbitmq getRabbitmq() {
        return rabbitmq;
    }

    public H2Console getH2Console() {
        return h2Console;
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Redis {
        /**
         * 与 yml 的 zhongbang.redis.enabled 对应，默认 false。
         * 选择内存还是 Redis 实现的是条件注解，不是这个字段。dev 配置另外排除了 Redis 自动配置。
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Rabbitmq {
        /** 与 yml 的 zhongbang.rabbitmq.enabled 对应，默认 false。本字段当前没有被读取。 */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class H2Console {
        /** 预留开关。当前没有代码读取它，安全配置始终拒绝 /h2-console。 */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
