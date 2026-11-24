package com.zhongbang.controller.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

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
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + userType.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
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
