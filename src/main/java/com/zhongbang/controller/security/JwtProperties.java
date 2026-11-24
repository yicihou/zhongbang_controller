package com.zhongbang.controller.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zhongbang.jwt")
public class JwtProperties {

    /** HMAC secret; override via JWT_SECRET in non-dev environments. */
    private String secret = "zhongbang-dev-only-secret-change-me-32bytes!!";
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
