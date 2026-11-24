package com.zhongbang.controller.service.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.pojo.customer.dto.AssetBindRequest;
import com.zhongbang.controller.pojo.customer.dto.AssetResponse;
import com.zhongbang.controller.pojo.customer.entity.CustomerAsset;
import com.zhongbang.controller.mapper.customer.CustomerAssetMapper;
import com.zhongbang.controller.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备档案规则。控制器只负责 HTTP，查谁的设备、能不能绑，在这里决定。
 * 通过 {@code UserContext} 读当前客户 id，不信任请求体里的客户 id（请求体里也没有）。
 */
@Service
public class AssetService {

    private final CustomerAssetMapper customerAssetMapper;

    public AssetService(CustomerAssetMapper customerAssetMapper) {
        this.customerAssetMapper = customerAssetMapper;
    }

    /** 只读事务：按客户 id 查询并转成响应对象。 */
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
     * 占位绑定：不查出厂序列号，也不查订单。
     * 已有且属于当前客户则直接返回；属于别人则抛业务码 4090；没有则插入一行假档案
     * （机型「待完善机型」，购买日今天，保修两年）。sn 先 trim。
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
