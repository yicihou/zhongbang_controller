package com.zhongbang.controller.controller.product;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.product.vo.ProductVo;
import com.zhongbang.controller.service.product.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 小程序首页和分类。路径在安全配置里公开。
 * 首页的热销就是上架商品的前几条，不再返回写死的空列表。
 */
@RestController
public class CatalogController {

    private final ProductService productService;

    public CatalogController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/api/home")
    public ApiResponse<Map<String, Object>> home() {
        return ApiResponse.ok(productService.home());
    }

    @GetMapping("/api/categories")
    public ApiResponse<List<ProductVo.Category>> categories() {
        return ApiResponse.ok(productService.categories());
    }
}
