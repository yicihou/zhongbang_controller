package com.zhongbang.controller.security;

import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 在 Service 里取出「当前是谁」。数据来自 JWT 过滤器放进安全上下文的 {@code LoginUser}。
 * 受保护接口在进控制器前已经拦过未登录请求；这里再查一次，是避免 Service 被别处空上下文调用时悄悄用错用户。
 */
public final class UserContext {

    private UserContext() {
    }

    /** 没有登录用户时抛业务异常，错误码 2001，HTTP 401。 */
    public static LoginUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    /** 公开接口里想「有登录就用、没有也行」时使用。当前没有任何调用方。 */
    public static LoginUser currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            return null;
        }
        return user;
    }
}
