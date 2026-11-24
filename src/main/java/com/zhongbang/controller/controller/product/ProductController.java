package com.zhongbang.controller.controller.product;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 商品公开接口的空壳，不查 {@code product} 表。
 * 列表永远是空的一页；详情只把路径里的 id 原样放进一个 Map，并带 {@code note=product-detail-shell}，
 * 用来标明这不是真实商品。id 不存在也不会 404。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /** page 默认 1，size 默认 20，都会再被收进 1–100。total 固定 0。 */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        return ApiResponse.ok(PageResult.of(List.of(), 0, p, s));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("id", id, "name", "", "note", "product-detail-shell"));
    }
}
