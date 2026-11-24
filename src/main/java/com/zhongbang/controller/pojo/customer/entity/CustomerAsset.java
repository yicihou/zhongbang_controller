package com.zhongbang.controller.pojo.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户名下的一台机器。表上没有 deleted 列，不受全局逻辑删除配置影响。
 * 绑定占位时 model 会写成「待完善机型」，保修截止日是绑定当天加两年，并不是查出来的真实保修。
 */
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
    /** INTERNAL 本店或绑定；EXTERNAL 报修时登记的外购。空则交给数据库默认值 INTERNAL。 */
    private String source;
    private String brand;
    private String purchaseChannel;
    private String purchaseProofUrl;

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
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getPurchaseChannel() { return purchaseChannel; }
    public void setPurchaseChannel(String purchaseChannel) { this.purchaseChannel = purchaseChannel; }
    public String getPurchaseProofUrl() { return purchaseProofUrl; }
    public void setPurchaseProofUrl(String purchaseProofUrl) { this.purchaseProofUrl = purchaseProofUrl; }
}
