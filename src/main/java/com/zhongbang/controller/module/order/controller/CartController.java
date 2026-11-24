package com.zhongbang.controller.module.order.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.module.order.dto.CartItemRequest;
import com.zhongbang.controller.module.order.dto.CartItemResponse;
import com.zhongbang.controller.module.order.service.CartService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<List<CartItemResponse>> list() {
        return ApiResponse.ok(cartService.listMine());
    }

    @PostMapping
    public ApiResponse<CartItemResponse> add(@Valid @RequestBody CartItemRequest request) {
        return ApiResponse.ok(cartService.add(request));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<CartItemResponse> update(@PathVariable Long id, @Valid @RequestBody CartItemRequest request) {
        return ApiResponse.ok(cartService.update(id, request));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        cartService.remove(id);
        return ApiResponse.ok();
    }
}
