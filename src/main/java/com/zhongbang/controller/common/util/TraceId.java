package com.zhongbang.controller.common.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 给一次请求一个短编号，写进响应头、响应体和日志，方便把同一次调用的日志搜出来。
 * 由 {@code TraceIdFilter} 在进业务之前写入，请求结束时清掉。
 * 工具类不能 new，所以构造器是私有的。
 */
public final class TraceId {

    /** 调用方可以带这个请求头；没有就由本服务生成。响应里也会回写同名头。 */
    public static final String HEADER = "X-Trace-Id";
    /** 日志格式里的 {@code %X{traceId}} 读的就是这个键。 */
    public static final String MDC_KEY = "traceId";

    private TraceId() {
    }

    /** 当前线程上的跟踪号。还没经过过滤器时返回空字符串，而不是 null。 */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? "" : value;
    }

    /**
     * 采用调用方传入的跟踪号；空则生成 16 位十六进制（去掉连字符的 UUID 前半段）并放入日志上下文。
     *
     * @param incoming 请求头 {@code X-Trace-Id}，可以为 null
     */
    public static String ensure(String incoming) {
        String value = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : incoming.trim();
        MDC.put(MDC_KEY, value);
        return value;
    }

    /** 请求结束必须调用，否则线程被复用时，下一个请求会带上旧的跟踪号。 */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
