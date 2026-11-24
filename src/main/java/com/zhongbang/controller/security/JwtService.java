package com.zhongbang.controller.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 签发和校验 JWT。登录成功时 {@code AuthService} 调用 {@code issue}；
 * 之后每个请求由 {@code JwtAuthenticationFilter} 调用 {@code parse}。
 *
 * <p>JWT 是一段带签名的字符串，服务器不保存会话。令牌里有用户 id（subject）、
 * {@code type}（CUSTOMER / STAFF / TECHNICIAN）、{@code name}、签发时间和过期时间。
 * 签名算法是 HMAC-SHA，密钥来自配置。校验失败会抛运行时异常，过滤器负责接住。
 *
 * <p>{@code @Service} 和 {@code @Component} 一样会放进容器，习惯上用来标业务类。
 */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        // JJWT 要求 HMAC 密钥至少 256 位（32 字节），太短就拒绝启动，避免用一个能被猜到的密钥上线。
        if (bytes.length < 32) {
            throw new IllegalStateException("zhongbang.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** 按用户 id、类型和显示名签发令牌。过期时间是当前时间加上配置的秒数。 */
    public String issue(LoginUser user) {
        return issue(user, null);
    }

    /**
     * 员工登录多写一个 {@code role} 声明，例如 ROLE_ADMIN。
     * 管理端认的是 type=STAFF；role 让令牌里能看出这是管理员还是普通员工。
     */
    public String issue(LoginUser user, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getExpiresSeconds());
        var builder = Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("type", user.getUserType().name())
                .claim("name", user.getDisplayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp));
        if (role != null && !role.isBlank()) {
            builder.claim("role", role);
        }
        return builder.signWith(key).compact();
    }

    /**
     * 验签并读出用户。过期、签名不对、type 不是已知枚举时抛异常。
     * 不查数据库，所以令牌在过期前不会因为用户被删而立刻失效。
     */
    public LoginUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        UserType type = UserType.valueOf(claims.get("type", String.class));
        String name = claims.get("name", String.class);
        return new LoginUser(userId, type, name);
    }

    public long expiresSeconds() {
        return properties.getExpiresSeconds();
    }
}
