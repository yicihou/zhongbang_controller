package com.zhongbang.controller.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("zhongbang.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issue(LoginUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getExpiresSeconds());
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("type", user.getUserType().name())
                .claim("name", user.getDisplayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

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
