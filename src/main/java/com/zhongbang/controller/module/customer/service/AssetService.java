package com.zhongbang.controller.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.customer.dto.AssetBindRequest;
import com.zhongbang.controller.module.customer.dto.AssetResponse;
import com.zhongbang.controller.module.customer.entity.CustomerAsset;
import com.zhongbang.controller.module.customer.mapper.CustomerAssetMapper;
import com.zhongbang.controller.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssetService {

    private final CustomerAssetMapper customerAssetMapper;

    public AssetService(CustomerAssetMapper customerAssetMapper) {
        this.customerAssetMapper = customerAssetMapper;
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> listMine() {
        Long customerId = UserContext.require().getUserId();
        return customerAssetMapper.selectList(new LambdaQueryWrapper<CustomerAsset>()
                        .eq(CustomerAsset::getCustomerId, customerId)
                        .orderByDesc(CustomerAsset::getId))
                .stream()
                .map(AssetResponse::from)
                .toList();
    }

    /**
     * Skeleton bind: creates a placeholder archive for the SN if missing.
     * Real flow should look up factory SN / order history first.
     */
    @Transactional
    public AssetResponse bind(AssetBindRequest request) {
        Long customerId = UserContext.require().getUserId();
        String sn = request.sn().trim();
        CustomerAsset existing = customerAssetMapper.selectOne(new LambdaQueryWrapper<CustomerAsset>()
                .eq(CustomerAsset::getSn, sn)
                .last("LIMIT 1"));
        if (existing != null) {
            if (!customerId.equals(existing.getCustomerId())) {
                throw new BusinessException(ErrorCode.CONFLICT, "该设备已绑定其他客户");
            }
            return AssetResponse.from(existing);
        }
        CustomerAsset asset = new CustomerAsset();
        asset.setTenantId(1L);
        asset.setCustomerId(customerId);
        asset.setModel("待完善机型");
        asset.setSn(sn);
        asset.setBuyDate(LocalDate.now());
        asset.setWarrantyEnd(LocalDate.now().plusYears(2));
        asset.setCreatedAt(LocalDateTime.now());
        customerAssetMapper.insert(asset);
        return AssetResponse.from(asset);
    }
}
