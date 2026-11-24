package com.zhongbang.controller.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhongbang.controller.common.enums.OrderStatus;

import java.time.LocalDateTime;

@TableName("orders")
public class SalesOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String orderNo;
    private Long customerId;
    private Long storeId;
    private Long totalAmount;
    private Long depositAmount;
    private Long paidAmount;
    private Long balanceAmount;
    private OrderStatus status;
    private String source;
    private Long goodsAmount;
    private Long installAmount;
    private Long freightAmount;
    private Long adjustAmount;
    private Long payableAmount;
    private String contactRemark;
    private String adminRemark;
    private Integer priceAdjusted;
    private Long addressId;
    private String addressJson;
    private String remark;
    private LocalDateTime payTime;
    private LocalDateTime finishTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public Long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Long totalAmount) { this.totalAmount = totalAmount; }
    public Long getDepositAmount() { return depositAmount; }
    public void setDepositAmount(Long depositAmount) { this.depositAmount = depositAmount; }
    public Long getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Long paidAmount) { this.paidAmount = paidAmount; }
    public Long getBalanceAmount() { return balanceAmount; }
    public void setBalanceAmount(Long balanceAmount) { this.balanceAmount = balanceAmount; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getGoodsAmount() { return goodsAmount; }
    public void setGoodsAmount(Long goodsAmount) { this.goodsAmount = goodsAmount; }
    public Long getInstallAmount() { return installAmount; }
    public void setInstallAmount(Long installAmount) { this.installAmount = installAmount; }
    public Long getFreightAmount() { return freightAmount; }
    public void setFreightAmount(Long freightAmount) { this.freightAmount = freightAmount; }
    public Long getAdjustAmount() { return adjustAmount; }
    public void setAdjustAmount(Long adjustAmount) { this.adjustAmount = adjustAmount; }
    public Long getPayableAmount() { return payableAmount; }
    public void setPayableAmount(Long payableAmount) { this.payableAmount = payableAmount; }
    public String getContactRemark() { return contactRemark; }
    public void setContactRemark(String contactRemark) { this.contactRemark = contactRemark; }
    public String getAdminRemark() { return adminRemark; }
    public void setAdminRemark(String adminRemark) { this.adminRemark = adminRemark; }
    public Integer getPriceAdjusted() { return priceAdjusted; }
    public void setPriceAdjusted(Integer priceAdjusted) { this.priceAdjusted = priceAdjusted; }
    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }
    public String getAddressJson() { return addressJson; }
    public void setAddressJson(String addressJson) { this.addressJson = addressJson; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
    public LocalDateTime getFinishTime() { return finishTime; }
    public void setFinishTime(LocalDateTime finishTime) { this.finishTime = finishTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
