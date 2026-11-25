package com.zhongbang.controller.security;

import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;

/**
 * 判断当前登录员工是不是老板。
 * 客户档案、协议还款、分析和员工管理只允许角色 BOSS。管理员和业务员的令牌进得了别的后台接口，到这里会被拒绝。
 */
public final class StaffRoles {

    private StaffRoles() {
    }

    public static boolean isBoss(LoginUser user) {
        if (user == null) {
            return false;
        }
        return user.getAuthorities().stream().anyMatch(auth -> "ROLE_BOSS".equals(auth.getAuthority()));
    }

    public static void requireBoss() {
        requireBoss("只有老板可以查看客户档案");
    }

    public static void requireBoss(String message) {
        LoginUser user = UserContext.require();
        if (!isBoss(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }
}
