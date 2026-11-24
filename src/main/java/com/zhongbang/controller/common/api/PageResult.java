package com.zhongbang.controller.common.api;

import java.util.List;

/**
 * 分页外壳，和接口文档一致：页码从 1 开始，列表字段名是 {@code list}（不是 content / records）。
 * 目前只有商品空壳接口在用。它不自己查数据库，只装已经取好的一页数据。
 */
public record PageResult<T>(
        List<T> list,
        long total,
        int page,
        int size
) {

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }

    /** 小于 1 的页码当成第 1 页。 */
    public static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /** 每页条数限制在 1 到 100，避免一次拉太多。 */
    public static int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
