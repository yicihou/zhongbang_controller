package com.zhongbang.controller.config;

import com.zhongbang.controller.common.util.TraceId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每个 HTTP 请求最先跑的过滤器：读或生成跟踪号，写入响应头和日志上下文，请求结束后清掉。
 *
 * <p>{@code OncePerRequestFilter} 保证同一次请求里只执行一遍（避免 forward 时重复）。
 * {@code @Order(HIGHEST_PRECEDENCE)} 让它排在 Spring Security 前面，这样 401 的 JSON 里也带得上 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    /** 用 finally 清理，是为了异常或中途返回时也不会把跟踪号留在线程上。 */
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String traceId = TraceId.ensure(request.getHeader(TraceId.HEADER));
            response.setHeader(TraceId.HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            TraceId.clear();
        }
    }
}
