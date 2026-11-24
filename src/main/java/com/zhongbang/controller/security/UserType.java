package com.zhongbang.controller.security;

/**
 * 令牌里的用户类型，会写进 JWT 的 {@code type} 声明，并变成 {@code ROLE_} 加这个名字。
 * 现有登录只签发 {@code CUSTOMER}。{@code STAFF}、{@code TECHNICIAN} 还没有签发接口。
 */
public enum UserType {
    CUSTOMER,
    STAFF,
    TECHNICIAN
}
