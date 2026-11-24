package com.zhongbang.controller.common.exception;

/**
 * 可预期的业务失败，例如参数不合法、设备已被别人绑定。
 * 调用方抛出后不必自己写 ResponseEntity，{@code GlobalExceptionHandler} 会转成统一 JSON。
 * 这是运行时异常，方法签名上不用声明 throws。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 使用错误码自带的文案。 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 错误码决定 HTTP 状态的大致分类，message 覆盖返回给前端的句子。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
