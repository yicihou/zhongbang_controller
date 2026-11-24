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
 * <p>角色字符串是 {@code ROLE_} 加上用户类型，例如 {@code ROLE_CUSTOMER}。
 * {@code hasRole("CUSTOMER")} 会自动补这个前缀。密码固定为空，账号状态方法都返回可用，
 * 因为本骨架不做锁定和过期账号，过期只由 JWT 的 exp 决定。
 */
public class LoginUser implements UserDetails {

    private final Long userId;
    private final UserType userType;
    private final String displayName;

    public LoginUser(Long userId, UserType userType, String displayName) {
        this.userId = userId;
        this.userType = userType;
        this.displayName = displayName == null ? "" : displayName;
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
    /** 只有一个权限：ROLE_CUSTOMER、ROLE_STAFF 或 ROLE_TECHNICIAN。 */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + userType.name()));
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
