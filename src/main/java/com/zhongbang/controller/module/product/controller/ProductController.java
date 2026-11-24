package com.zhongbang.controller.module.product.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Public product shell aligned with /api/products. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

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
