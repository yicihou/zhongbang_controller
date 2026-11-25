package com.zhongbang.controller.pojo.staff.vo;

import java.util.List;

/**
 * 员工管理列表里的一行。
 * {@code ref} 是页面提交更新时用的键：{@code user-1} 表示登录账号，{@code tech-88} 表示还没开通登录的师傅。
 * 不返回密码。
 */
public record StaffMemberVo(
        String ref,
        Long userId,
        Long technicianId,
        String name,
        String mobile,
        String role,
        String status,
        String remark,
        String username,
        boolean loginAccount,
        List<String> skills,
        String region
) {
}
