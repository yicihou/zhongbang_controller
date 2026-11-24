package com.zhongbang.controller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
        /** When false, in-memory idempotency/lock fallbacks are used and Redis auto-config is skipped. */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Rabbitmq {
        /** When false, a no-op message publisher is used and AMQP auto-config is skipped. */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class H2Console {
        /** Explicit opt-in; Security still denies /h2-console by default. */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
