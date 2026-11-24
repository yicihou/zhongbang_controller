package com.zhongbang.controller.controller.product;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.product.dto.ProductRequests;
import com.zhongbang.controller.pojo.product.vo.ProductVo;
import com.zhongbang.controller.service.product.ProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端商品。路径在 {@code /api/admin/**} 下面，要员工或管理员的 STAFF 令牌。
 * 这里只接收 JSON 并调用 {@code ProductService}，上下架和改价的判断在服务里。
 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<PageResult<ProductVo.Admin>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(productService.adminList(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductVo.Admin> detail(@PathVariable Long id) {
        return ApiResponse.ok(productService.adminGet(id));
    }

    @PostMapping
    public ApiResponse<ProductVo.Admin> create(@RequestBody ProductRequests.Save request) {
        return ApiResponse.ok(productService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductVo.Admin> update(@PathVariable Long id, @RequestBody ProductRequests.Save request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok();
    }

    /** 上架。之后小程序列表就能看到。 */
    @PostMapping("/{id}/on-shelf")
    public ApiResponse<ProductVo.Admin> onShelf(@PathVariable Long id) {
        return ApiResponse.ok(productService.shelf(id, true));
    }

    /** 下架。小程序列表和详情都不再返回它。 */
    @PostMapping("/{id}/off-shelf")
    public ApiResponse<ProductVo.Admin> offShelf(@PathVariable Long id) {
        return ApiResponse.ok(productService.shelf(id, false));
    }

    /** 改价。body 里 price 是分，skuId 可以不传。 */
    @PostMapping("/{id}/price")
    public ApiResponse<ProductVo.Admin> price(@PathVariable Long id, @RequestBody ProductRequests.Price request) {
        return ApiResponse.ok(productService.changePrice(id, request));
    }
}
