package com.zhongbang.controller.adapter.deepseek;

/** 调用 DeepSeek 失败时抛出。消息是给老板看的中文，不要把密钥或堆栈放进来。 */
public class DeepSeekException extends RuntimeException {

    public DeepSeekException(String message) {
        super(message);
    }
}
