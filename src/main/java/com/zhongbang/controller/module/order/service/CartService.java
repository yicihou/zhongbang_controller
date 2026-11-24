package com.zhongbang.controller.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.enums.DeliveryMethod;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.order.dto.CartItemRequest;
import com.zhongbang.controller.module.order.dto.CartItemResponse;
import com.zhongbang.controller.module.order.entity.CartItem;
import com.zhongbang.controller.module.order.mapper.CartItemMapper;
import com.zhongbang.controller.module.product.entity.Product;
import com.zhongbang.controller.module.product.entity.ProductSku;
import com.zhongbang.controller.module.product.mapper.ProductMapper;
import com.zhongbang.controller.module.product.mapper.ProductSkuMapper;
import com.zhongbang.controller.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;

    public CartService(CartItemMapper cartItemMapper, ProductSkuMapper productSkuMapper, ProductMapper productMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
    }

    public List<CartItemResponse> listMine() {
        Long customerId = UserContext.require().getUserId();
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getCustomerId, customerId)
                .orderByDesc(CartItem::getUpdatedAt));
        List<CartItemResponse> result = new ArrayList<>();
        for (CartItem item : items) {
            result.add(toResponse(item));
        }
        return result;
    }

    @Transactional
    public CartItemResponse add(CartItemRequest request) {
        Long customerId = UserContext.require().getUserId();
        ProductSku sku = requireSku(request.skuId());
        DeliveryMethod method = request.deliveryMethod() == null ? DeliveryMethod.EXPRESS : request.deliveryMethod();
        CartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getCustomerId, customerId)
                .eq(CartItem::getSkuId, request.skuId())
                .eq(CartItem::getDeliveryMethod, method)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.quantity());
            existing.setNeedInstall(method.requiresInstall() ? 1 : 0);
            existing.setUpdatedAt(now);
            cartItemMapper.updateById(existing);
            return toResponse(existing, sku);
        }
        CartItem item = new CartItem();
        item.setTenantId(1L);
        item.setCustomerId(customerId);
        item.setSkuId(request.skuId());
        item.setQuantity(request.quantity());
        item.setDeliveryMethod(method);
        item.setNeedInstall(method.requiresInstall() ? 1 : 0);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        cartItemMapper.insert(item);
        return toResponse(item, sku);
    }

    @Transactional
    public CartItemResponse update(Long id, CartItemRequest request) {
        CartItem item = requireMine(id);
        if (request.quantity() != null) {
            item.setQuantity(request.quantity());
        }
        if (request.deliveryMethod() != null) {
            item.setDeliveryMethod(request.deliveryMethod());
            item.setNeedInstall(request.deliveryMethod().requiresInstall() ? 1 : 0);
        }
        if (request.skuId() != null && !request.skuId().equals(item.getSkuId())) {
            requireSku(request.skuId());
            item.setSkuId(request.skuId());
        }
        item.setUpdatedAt(LocalDateTime.now());
        cartItemMapper.updateById(item);
        return toResponse(item);
    }

    @Transactional
    public void remove(Long id) {
        CartItem item = requireMine(id);
        cartItemMapper.deleteById(item.getId());
    }

    @Transactional
    public void clearMine() {
        Long customerId = UserContext.require().getUserId();
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getCustomerId, customerId));
    }

    private CartItem requireMine(Long id) {
        Long customerId = UserContext.require().getUserId();
        CartItem item = cartItemMapper.selectById(id);
        if (item == null || !customerId.equals(item.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "购物车行不存在");
        }
        return item;
    }

    private ProductSku requireSku(Long skuId) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || (sku.getDeleted() != null && sku.getDeleted() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在");
        }
        return sku;
    }

    private CartItemResponse toResponse(CartItem item) {
        return toResponse(item, requireSku(item.getSkuId()));
    }

    private CartItemResponse toResponse(CartItem item, ProductSku sku) {
        Product product = productMapper.selectById(sku.getProductId());
        String productName = product == null ? "" : product.getName();
        long line = sku.getPrice() * item.getQuantity();
        return new CartItemResponse(
                item.getId(),
                item.getSkuId(),
                productName,
                sku.getTitle(),
                sku.getPrice(),
                item.getQuantity(),
                item.getDeliveryMethod(),
                item.getNeedInstall() != null && item.getNeedInstall() == 1,
                line
        );
    }
}
