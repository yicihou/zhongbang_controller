package com.zhongbang.controller.pojo.auth.dto;

/**
 * 员工登录成功的 data。token 放进 Authorization 头。
 * {@code staff.type} 固定 STAFF，{@code staff.role} 是 ROLE_ 加上表里的角色，例如 ROLE_ADMIN。
 * JWT 的 type 声明也是 STAFF，所以管理端路由和 {@code hasRole("STAFF")} 都能认。
 */
public record StaffLoginResponse(
        String token,
        long expiresIn,
        StaffBrief staff
) {
    public record StaffBrief(Long id, String name, String type, String role) {
    }
}
