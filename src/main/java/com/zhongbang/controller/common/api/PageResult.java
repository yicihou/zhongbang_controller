package com.zhongbang.controller.common.api;

import java.util.List;

/** Pagination envelope aligned with docs/OpenAPI: page starts at 1, field name {@code list}. */
public record PageResult<T>(
        List<T> list,
        long total,
        int page,
        int size
) {

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }

    public static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    public static int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
