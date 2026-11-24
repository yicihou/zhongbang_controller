package com.zhongbang.controller.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        ListOrEmpty origins = ListOrEmpty.of(corsProperties.getAllowedOrigins());
        registry.addMapping("/api/**")
                .allowedOrigins(origins.values())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    private record ListOrEmpty(String[] values) {
        static ListOrEmpty of(java.util.List<String> list) {
            if (list == null || list.isEmpty()) {
                return new ListOrEmpty(new String[0]);
            }
            return new ListOrEmpty(list.toArray(String[]::new));
        }
    }
}
