package com.zhongbang.controller.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("customer_asset")
public class CustomerAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long customerId;
    private Long skuId;
    private String model;
    private String sn;
    private LocalDate buyDate;
    private LocalDate warrantyEnd;
    private Long installOrder;
    private LocalDateTime lastServiceAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }
    public LocalDate getBuyDate() { return buyDate; }
    public void setBuyDate(LocalDate buyDate) { this.buyDate = buyDate; }
    public LocalDate getWarrantyEnd() { return warrantyEnd; }
    public void setWarrantyEnd(LocalDate warrantyEnd) { this.warrantyEnd = warrantyEnd; }
    public Long getInstallOrder() { return installOrder; }
    public void setInstallOrder(Long installOrder) { this.installOrder = installOrder; }
    public LocalDateTime getLastServiceAt() { return lastServiceAt; }
    public void setLastServiceAt(LocalDateTime lastServiceAt) { this.lastServiceAt = lastServiceAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
