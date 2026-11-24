package com.zhongbang.controller.adapter.wx;

import org.springframework.stereotype.Component;

/**
 * WeChat login adapter stub. Replace with WxJava jscode2session in real integration.
 */
@Component
public class WxAuthAdapter {

    public WxSession exchangeCode(String code) {
        // Local/dev: treat code as stable openid seed so login is testable without WeChat.
        String openid = "dev_" + code;
        return new WxSession(openid, null);
    }

    public record WxSession(String openid, String sessionKey) {
    }
}
