package com.zhongbang.controller.pojo.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 一张报修单。客户一次可以报多台设备，设备明细在 {@link RepairLine}。
 * 金额单位是分。{@code quoteAmount} 还没报价时为空。
 */
@TableName("repair_ticket")
public class RepairTicket {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String ticketNo;
    private Long customerId;
    private String customerName;
    private String status;
    private String expectDate;
    private String expectSlot;
    private String contactMobile;
    private String remark;
    private Long quoteAmount;
    private Long payableAmount;
    private Long paidAmount;
    private Integer inWarranty;
    /** 0 不开票，1 开票。和订单用同一套税率：1300 表示 13%。 */
    private Integer invoiced;
    private String invoiceType;
    private Integer taxRate;
    private Integer taxIncluded;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getTicketNo() { return ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExpectDate() { return expectDate; }
    public void setExpectDate(String expectDate) { this.expectDate = expectDate; }
    public String getExpectSlot() { return expectSlot; }
    public void setExpectSlot(String expectSlot) { this.expectSlot = expectSlot; }
    public String getContactMobile() { return contactMobile; }
    public void setContactMobile(String contactMobile) { this.contactMobile = contactMobile; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getQuoteAmount() { return quoteAmount; }
    public void setQuoteAmount(Long quoteAmount) { this.quoteAmount = quoteAmount; }
    public Long getPayableAmount() { return payableAmount; }
    public void setPayableAmount(Long payableAmount) { this.payableAmount = payableAmount; }
    public Long getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Long paidAmount) { this.paidAmount = paidAmount; }
    public Integer getInWarranty() { return inWarranty; }
    public void setInWarranty(Integer inWarranty) { this.inWarranty = inWarranty; }
    public Integer getInvoiced() { return invoiced; }
    public void setInvoiced(Integer invoiced) { this.invoiced = invoiced; }
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    public Integer getTaxRate() { return taxRate; }
    public void setTaxRate(Integer taxRate) { this.taxRate = taxRate; }
    public Integer getTaxIncluded() { return taxIncluded; }
    public void setTaxIncluded(Integer taxIncluded) { this.taxIncluded = taxIncluded; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
