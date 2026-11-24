package com.zhongbang.controller.common.exception;

/**
 * 业务错误码，写在响应 JSON 的 {@code code} 字段里，和 HTTP 状态码不是同一个数。
 * 例如未登录是业务码 2001，HTTP 状态是 401。
 *
 * <p>库存、档期、金额、支付这几项已经编号，但对应的下单和支付代码还没写，目前不会被抛出。
 * 登录占位、设备冲突、参数校验会用到其中一部分。
 */
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
