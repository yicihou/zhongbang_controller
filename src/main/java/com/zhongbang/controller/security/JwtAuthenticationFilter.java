package com.zhongbang.controller.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在 Spring Security 的用户名密码过滤器之前执行：看请求头里有没有 {@code Authorization: Bearer} 加令牌。
 *
 * <p>有令牌且能解析，就把 {@code LoginUser} 放进当前请求的安全上下文，后面的 {@code hasRole} 才能读到角色。
 * 没有头、或令牌解析失败，就清空上下文并继续往下走，不在这里直接返回 401。
 * 该不该登录由 {@code SecurityConfig} 的路径规则决定：公开接口照常进入控制器，受保护接口随后被入口点拒绝。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    /**
     * 只处理以 {@code Bearer } 开头的头。令牌前后空白会去掉。
     * 解析抛出的任何异常都被吃掉，避免一个坏令牌把公开接口也打成 500。
     */
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    LoginUser user = jwtService.parse(token);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
