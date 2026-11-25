package com.zhongbang.controller.pojo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单头，对应表 {@code orders}。新代码读写 goods/install/freight/payable 这些列。
 * V1 留下的 total_amount、deposit_amount、balance_amount 在保存时同步成应付和已付，避免旧列是空的。
 */
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
    private String status;
    private String source;
    private LocalDate installDate;
    private String installSlot;
    private Long technicianId;
    private String addressJson;
    private String remark;
    private LocalDateTime payTime;
    private LocalDateTime finishTime;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long goodsAmount;
    private Long installAmount;
    private Long freightAmount;
    private Long adjustAmount;
    private Long payableAmount;
    private String contactRemark;
    private String adminRemark;
    private Integer priceAdjusted;
    private String addressText;
    private String customerName;
    private String customerMobile;
    private String requestId;
    /** 0 不开票，1 开票。税率是百分数乘 100，1300 表示 13%。 */
    private Integer invoiced;
    private String invoiceType;
    private Integer taxRate;
    private Integer taxIncluded;

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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDate getInstallDate() { return installDate; }
    public void setInstallDate(LocalDate installDate) { this.installDate = installDate; }
    public String getInstallSlot() { return installSlot; }
    public void setInstallSlot(String installSlot) { this.installSlot = installSlot; }
    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
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
    public String getAddressText() { return addressText; }
    public void setAddressText(String addressText) { this.addressText = addressText; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerMobile() { return customerMobile; }
    public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Integer getInvoiced() { return invoiced; }
    public void setInvoiced(Integer invoiced) { this.invoiced = invoiced; }
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    public Integer getTaxRate() { return taxRate; }
    public void setTaxRate(Integer taxRate) { this.taxRate = taxRate; }
    public Integer getTaxIncluded() { return taxIncluded; }
    public void setTaxIncluded(Integer taxIncluded) { this.taxIncluded = taxIncluded; }
}
