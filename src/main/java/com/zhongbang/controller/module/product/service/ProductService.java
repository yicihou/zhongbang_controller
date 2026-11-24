package com.zhongbang.controller.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.product.entity.Product;
import com.zhongbang.controller.module.product.entity.ProductSku;
import com.zhongbang.controller.module.product.mapper.ProductMapper;
import com.zhongbang.controller.module.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    public ProductService(ProductMapper productMapper, ProductSkuMapper productSkuMapper) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
    }

    public PageResult<Map<String, Object>> list(int page, int size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        Page<Product> mp = productMapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, "ON")
                        .orderByAsc(Product::getId));
        List<Map<String, Object>> list = mp.getRecords().stream().map(this::brief).collect(Collectors.toList());
        return PageResult.of(list, mp.getTotal(), p, s);
    }

    public Map<String, Object> detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || (product.getDeleted() != null && product.getDeleted() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        List<ProductSku> skus = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, id)
                .orderByAsc(ProductSku::getId));
        Map<String, Object> data = brief(product);
        List<Map<String, Object>> skuList = new ArrayList<>();
        for (ProductSku sku : skus) {
            Map<String, Object> s = new HashMap<>();
            s.put("id", sku.getId());
            s.put("title", sku.getTitle());
            s.put("price", sku.getPrice());
            s.put("stock", sku.getStock());
            skuList.add(s);
        }
        data.put("skus", skuList);
        return data;
    }

    private Map<String, Object> brief(Product product) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", product.getId());
        m.put("name", product.getName());
        m.put("brand", product.getBrand());
        m.put("category", product.getCategory());
        m.put("status", product.getStatus());
        return m;
    }
}
