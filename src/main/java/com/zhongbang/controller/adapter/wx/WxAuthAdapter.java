package com.zhongbang.controller.adapter.wx;

import org.springframework.stereotype.Component;

/**
 * 微信登录占位。由 {@code AuthService} 在客户登录时调用。
 *
 * <p>正式做法是把小程序 code 交给微信，换回 openid 和 session_key。
 * 这里不发网络请求：同一个 code 永远得到同一个 {@code dev_} 前缀的 openid，方便本地反复登录。
 * {@code sessionKey} 固定为 null，不能用来解密手机号。
 */
@Component
public class WxAuthAdapter {

    /**
     * @param code 小程序 {@code wx.login} 拿到的临时码；本地测试可以是任意非空字符串
     * @return openid 为 {@code "dev_" + code}，sessionKey 为 null
     */
    public WxSession exchangeCode(String code) {
        String openid = "dev_" + code;
        return new WxSession(openid, null);
    }

    /** 微信会话。当前 sessionKey 不会有值。 */
    public record WxSession(String openid, String sessionKey) {
    }
}
