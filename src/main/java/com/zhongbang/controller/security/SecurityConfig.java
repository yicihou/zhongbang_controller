package com.zhongbang.controller.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 过滤器链：每个请求按这里的规则决定放行、要登录，还是要某个角色。
 *
 * <p>{@code @EnableMethodSecurity} 让控制器上的 {@code @PreAuthorize} 生效（设备接口用来限制客户角色）。
 * 会话策略是 STATELESS：不创建 HttpSession，登录态只存在于 JWT。
 * CSRF 关掉，因为接口用 Authorization 头，不靠浏览器自动带的 Cookie。
 *
 * <p>路径规则自上而下，先匹配到的生效：
 * 登录、首页、分类、支付回调、探活、健康检查公开；商品的 GET 公开；
 * {@code /api/admin/**} 要 {@code ROLE_STAFF}；{@code /api/tech/**} 要 {@code ROLE_TECHNICIAN}；
 * 其余 Actuator 和 H2 控制台一律拒绝；其他路径只要登录即可。
 * 未登录访问「拒绝」或「要登录」的路径，实际先得到 401；已登录但角色不对才是 403。
 * 本仓库还没有 admin、tech、首页、分类、支付回调的控制器，路径只是先占上。
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * 组装一条过滤器链。JWT 过滤器插在用户名密码过滤器之前，这样后面的授权规则读得到角色。
     * frameOptions 设为同源，是给以后可能的内嵌页留的；H2 控制台路径本身仍被拒绝。
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/phone",
                                "/api/home",
                                "/api/categories",
                                "/api/pay/notify",
                                "/api/system/ping",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("STAFF")
                        .requestMatchers("/api/tech/**").hasRole("TECHNICIAN")
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/h2-console", "/h2-console/**").denyAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
