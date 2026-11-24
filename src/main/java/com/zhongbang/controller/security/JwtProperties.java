package com.zhongbang.controller.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 的签名密钥和有效期，前缀 {@code zhongbang.jwt}。由 {@code SecurityConfig} 启用绑定。
 * 密钥短于 32 字节时 {@code JwtService} 会在启动时拒绝。
 */
@ConfigurationProperties(prefix = "zhongbang.jwt")
public class JwtProperties {

    /** HMAC 密钥。非开发环境用环境变量 JWT_SECRET 覆盖 yml 里的默认值。 */
    private String secret = "zhongbang-dev-only-secret-change-me-32bytes!!";
    /** 有效期秒数，默认 2592000（30 天）。 */
    private long expiresSeconds = 2_592_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpiresSeconds() {
        return expiresSeconds;
    }

    public void setExpiresSeconds(long expiresSeconds) {
        this.expiresSeconds = expiresSeconds;
    }
}
