package com.zhongbang.controller.pojo.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一条协议还款。不物理删除：履约或作废只改 {@code status}，旧内容在日志表。
 * scope 为 CUSTOMER、ORDER 或 PAYMENT。
 */
@TableName("repay_agreement")
public class RepayAgreement {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long customerId;
    private String scope;
    private Long orderId;
    private Long paymentId;
    private LocalDate dueDate;
    private Long agreedAmount;
    private String status;
    private String note;
    private String materialsJson;
    private String operatorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Long getAgreedAmount() { return agreedAmount; }
    public void setAgreedAmount(Long agreedAmount) { this.agreedAmount = agreedAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getMaterialsJson() { return materialsJson; }
    public void setMaterialsJson(String materialsJson) { this.materialsJson = materialsJson; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
