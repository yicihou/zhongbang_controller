package com.zhongbang.controller.common.exception;

/** Business error codes aligned with docs §6.5. */
public enum ErrorCode {
    OK(0, "ok"),
    BAD_REQUEST(1001, "参数错误"),
    UNAUTHORIZED(2001, "未登录或 token 已过期"),
    FORBIDDEN(2003, "无权限"),
    STOCK_INSUFFICIENT(3001, "库存不足"),
    SLOT_CONFLICT(3002, "师傅档期冲突"),
    ILLEGAL_STATE(3003, "订单状态不允许该操作"),
    AMOUNT_MISMATCH(3004, "金额校验失败"),
    DUPLICATE_REQUEST(4001, "重复请求"),
    PAY_FAILED(5001, "支付下单失败"),
    NOT_FOUND(4040, "资源不存在"),
    CONFLICT(4090, "资源冲突"),
    INTERNAL_ERROR(5000, "服务内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
