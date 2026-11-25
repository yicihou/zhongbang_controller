package com.zhongbang.controller.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Web 层的少量自定义：跨域，以及把上传目录映射成 /uploads。
 *
 * <p>{@code @EnableConfigurationProperties} 让 {@code zhongbang.*} 绑定到 {@code ZhongbangProperties}。
 * 不写这行，yml 里的 cors 列表不会进这个类。
 */
@Configuration
@EnableConfigurationProperties(ZhongbangProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final ZhongbangProperties properties;

    public WebConfig(ZhongbangProperties properties) {
        this.properties = properties;
    }

    /**
     * 只对 {@code /api/**} 生效。允许的来源来自 {@code zhongbang.cors.allowed-origins}。
     * 列表为空就什么都不注册（同源或走开发代理时不需要跨域）。
     * 允许带 Cookie / Authorization（allowCredentials），预检结果缓存 3600 秒。
     * 默认来源是 localhost:3000；管理端 Vite 在 5173，README 建议用代理，避免改这里。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var origins = properties.getCors().getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 把 /uploads/** 映射到本机上传目录，这样收款凭证的 img 地址能直接打开。
     * 目录不存在时也不报错，等第一次上传再创建。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = properties.getStorage().directory();
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(properties.getStorage().publicPath() + "/**")
                .addResourceLocations(location);
    }
}
