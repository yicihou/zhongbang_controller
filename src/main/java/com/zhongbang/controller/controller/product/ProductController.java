package com.zhongbang.controller.controller.product;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.product.vo.ProductVo;
import com.zhongbang.controller.service.product.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序看商品。这两个 GET 在安全配置里是公开的，不需要登录。
 * 只返回上架商品。下架的详情会得到 404。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 列表。categoryId、keyword 都可以不传。page 从 1 开始。 */
    @GetMapping
    public ApiResponse<PageResult<ProductVo.Card>> list(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(productService.publicList(categoryId, keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductVo.Detail> detail(@PathVariable Long id) {
        return ApiResponse.ok(productService.publicDetail(id));
    }
}
