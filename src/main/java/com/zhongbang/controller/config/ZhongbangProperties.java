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
    private final Storage storage = new Storage();
    private final Deepseek deepseek = new Deepseek();
    private final CustomerModule customer = new CustomerModule();
    private final Demo demo = new Demo();

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

    public Storage getStorage() {
        return storage;
    }

    public Deepseek getDeepseek() {
        return deepseek;
    }

    public CustomerModule getCustomer() {
        return customer;
    }

    public Demo getDemo() {
        return demo;
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

    /**
     * 图片存在哪。type=local 时写到本机目录；以后换成 oss 只需要新的存储实现和这里的 type。
     * localDir 默认 ./uploads，相对启动程序时的工作目录，不放进 src，也不会打进 jar。
     */
    public static class Storage {
        private String type = "local";
        private String localDir = "./uploads";
        private String publicPath = "/uploads";
        private long maxBytes = 10L * 1024 * 1024;
        /** 录音上限，默认约 30MB。协议材料里的 mp3/m4a/wav/amr 用这个值。 */
        private long audioMaxBytes = 30L * 1024 * 1024;
        /** 文档上限，默认约 30MB。pdf/doc/docx/xls/xlsx 用这个值。 */
        private long docMaxBytes = 30L * 1024 * 1024;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLocalDir() {
            return localDir;
        }

        public void setLocalDir(String localDir) {
            this.localDir = localDir;
        }

        public String getPublicPath() {
            return publicPath;
        }

        public void setPublicPath(String publicPath) {
            this.publicPath = publicPath;
        }

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        public long getAudioMaxBytes() {
            return audioMaxBytes;
        }

        public void setAudioMaxBytes(long audioMaxBytes) {
            this.audioMaxBytes = audioMaxBytes;
        }

        public long getDocMaxBytes() {
            return docMaxBytes;
        }

        public void setDocMaxBytes(long docMaxBytes) {
            this.docMaxBytes = docMaxBytes;
        }

        /** 配置里写相对路径时，按启动目录补成绝对路径。末尾不带斜杠。 */
        public java.nio.file.Path directory() {
            String raw = localDir == null || localDir.isBlank() ? "./uploads" : localDir.trim();
            java.nio.file.Path path = java.nio.file.Path.of(raw);
            if (!path.isAbsolute()) {
                path = java.nio.file.Path.of("").toAbsolutePath().resolve(path);
            }
            return path.normalize();
        }

        /** 浏览器访问用的前缀，例如 /uploads。保证以 / 开头、不以 / 结尾。 */
        public String publicPath() {
            String path = publicPath == null || publicPath.isBlank() ? "/uploads" : publicPath.trim();
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return path;
        }
    }

    /**
     * DeepSeek 的 OpenAI 兼容接口。apiKey 只来自环境变量 DEEPSEEK_API_KEY，仓库里不放密钥。
     */
    public static class Deepseek {
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
        private String apiKey = "";
        private int timeoutSeconds = 25;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /** 客户模块里和协议还款提醒有关的配置。 */
    public static class CustomerModule {
        /** Spring 定时任务表达式，默认每天 9:00。时区在任务上写成上海。 */
        private String remindCron = "0 0 9 * * *";
        /** 到期前几天开始提醒。当天和已逾期另外判断。 */
        private int remindDays = 3;

        public String getRemindCron() {
            return remindCron;
        }

        public void setRemindCron(String remindCron) {
            this.remindCron = remindCron;
        }

        public int getRemindDays() {
            return remindDays;
        }

        public void setRemindDays(int remindDays) {
            this.remindDays = remindDays;
        }
    }

    /**
     * 演示客户开关。enabled 为 true 时，老板可以调用生成接口。
     * 生产配置保持 false。autoSeed 为 true 且当前是 dev 时，启动后自动灌一份数据。
     */
    public static class Demo {
        private boolean enabled = false;
        private boolean autoSeed = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoSeed() {
            return autoSeed;
        }

        public void setAutoSeed(boolean autoSeed) {
            this.autoSeed = autoSeed;
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
