package com.zhongbang.controller.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zhongbang.controller.common.util.TraceId;

/**
 * 所有接口共用的 JSON 外形：{@code code}、{@code msg}、{@code data}、{@code traceId}。
 * {@code code == 0} 且 {@code msg} 为 {@code ok} 表示成功。业务错误码见 {@code ErrorCode}，不是 HTTP 状态码。
 *
 * <p>{@code data} 为 null 时不输出该字段（类上的 {@code @JsonInclude}，以及 yml 里的全局 non_null）。
 * {@code traceId} 来自当前请求；过滤器还没写入时是空字符串。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int code,
        String msg,
        T data,
        String traceId
) {

    /** 成功并带上数据。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, TraceId.current());
    }

    /** 成功但没有数据体。JSON 里不会出现 data 字段。 */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null, TraceId.current());
    }

    /** 失败。data 固定为 null，因此响应里通常没有 data 字段。 */
    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg, null, TraceId.current());
    }
}
