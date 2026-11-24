package com.zhongbang.controller.pojo.auth.dto;

/**
 * 登录成功的 data。{@code token} 放进后续请求的 Authorization 头；
 * {@code expiresIn} 是秒，不是时间戳；{@code hasMobile} 表示库里有没有手机号（绑定接口还没写，新客户为 false）。
 * 头像目前固定空串。手机号会打码。
 */
public record LoginResponse(
        String token,
        long expiresIn,
        boolean hasMobile,
        CustomerBrief customer
) {
    /** 给前端展示的客户摘要，不是完整的 customer 表行。 */
    public record CustomerBrief(
            Long id,
            String nickname,
            String avatar,
            String mobile,
            int level,
            String levelText
    ) {
    }
}
