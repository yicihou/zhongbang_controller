package com.zhongbang.controller.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zhongbang.controller.common.util.TraceId;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int code,
        String msg,
        T data,
        String traceId
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, TraceId.current());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null, TraceId.current());
    }

    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg, null, TraceId.current());
    }
}
