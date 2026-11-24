package com.zhongbang.controller.module.aftersale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhongbang.controller.common.enums.RepairStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("repair_ticket")
public class RepairTicket {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String ticketNo;
    private Long customerId;
    private RepairStatus status;
    private LocalDate expectDate;
    private String expectSlot;
    private String contactMobile;
    private String remark;
    private Long quoteAmount;
    private Long payableAmount;
    private Long paidAmount;
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
    public RepairStatus getStatus() { return status; }
    public void setStatus(RepairStatus status) { this.status = status; }
    public LocalDate getExpectDate() { return expectDate; }
    public void setExpectDate(LocalDate expectDate) { this.expectDate = expectDate; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
