package com.zhongbang.controller.adapter.deepseek;

/**
 * 调用大模型的窄接口。业务代码只依赖它，测试里可以换成假的实现，避免真的访问外网。
 */
public interface DeepSeekClient {

    /**
     * @param systemPrompt 角色说明
     * @param userPrompt   已经脱敏的客户摘要
     * @return 模型返回的正文
     */
    String chat(String systemPrompt, String userPrompt);
}
