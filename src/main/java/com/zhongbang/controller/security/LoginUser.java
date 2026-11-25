package com.zhongbang.controller.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 当前登录者，放在 Spring Security 的上下文里。实现 {@code UserDetails} 是为了能放进
 * {@code UsernamePasswordAuthenticationToken}，并不是走用户名密码登录。
 *
 * <p>角色字符串是 {@code ROLE_} 加上用户类型，例如 {@code ROLE_CUSTOMER}、{@code ROLE_STAFF}。
 * 员工令牌还会多一个表里的角色，例如 {@code ROLE_ADMIN}，这样 {@code hasRole("ADMIN")} 才能认出管理员。
 * {@code hasRole("CUSTOMER")} 会自动补 {@code ROLE_} 前缀。密码固定为空，账号状态方法都返回可用，
 * 过期只由 JWT 的 exp 决定；停用账号在登录时拒绝，不在这里判断。
 */
public class LoginUser implements UserDetails {

    private final Long userId;
    private final UserType userType;
    private final String displayName;
    private final String staffRole;

    public LoginUser(Long userId, UserType userType, String displayName) {
        this(userId, userType, displayName, null);
    }

    /**
     * @param staffRole 员工表里的角色，可以是 ADMIN，也可以是已经带前缀的 ROLE_ADMIN。客户登录传 null。
     */
    public LoginUser(Long userId, UserType userType, String displayName, String staffRole) {
        this.userId = userId;
        this.userType = userType;
        this.displayName = displayName == null ? "" : displayName;
        this.staffRole = staffRole;
    }

    public Long getUserId() {
        return userId;
    }

    public UserType getUserType() {
        return userType;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    /**
     * 至少有一个权限：ROLE_CUSTOMER、ROLE_STAFF 或 ROLE_TECHNICIAN，来自令牌的 type。
     * 员工如果还有表里的角色，再加一个，例如 ROLE_ADMIN。两个不一样才会都放进去。
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + userType.name()));
        if (staffRole != null && !staffRole.isBlank()) {
            String role = staffRole.startsWith("ROLE_") ? staffRole : "ROLE_" + staffRole;
            if (!role.equals("ROLE_" + userType.name())) {
                authorities.add(new SimpleGrantedAuthority(role));
            }
        }
        return List.copyOf(authorities);
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    /** 不是登录名。形如 {@code CUSTOMER:123}，便于日志里区分人和类型。 */
    public String getUsername() {
        return userType.name() + ":" + userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
