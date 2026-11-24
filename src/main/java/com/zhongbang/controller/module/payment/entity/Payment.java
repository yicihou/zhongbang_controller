package com.zhongbang.controller.module.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhongbang.controller.common.enums.BizType;
import com.zhongbang.controller.common.enums.PaymentChannel;
import com.zhongbang.controller.common.enums.PaymentStatus;
import com.zhongbang.controller.common.enums.PaymentType;

import java.time.LocalDateTime;

@TableName("payment")
public class Payment {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long orderId;
    private String outTradeNo;
    @TableField("type")
    private PaymentType paymentType;
    private Long amount;
    private PaymentChannel channel;
    private PaymentStatus status;
    private String transactionId;
    private String prepayId;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BizType bizType;
    private Long bizId;
    private String paymentNo;
    private String title;
    private Integer sortNo;
    private String voucherUrlsJson;
    private Long operatorId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; }
    public PaymentType getPaymentType() { return paymentType; }
    public void setPaymentType(PaymentType paymentType) { this.paymentType = paymentType; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public PaymentChannel getChannel() { return channel; }
    public void setChannel(PaymentChannel channel) { this.channel = channel; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getPrepayId() { return prepayId; }
    public void setPrepayId(String prepayId) { this.prepayId = prepayId; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public BizType getBizType() { return bizType; }
    public void setBizType(BizType bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getVoucherUrlsJson() { return voucherUrlsJson; }
    public void setVoucherUrlsJson(String voucherUrlsJson) { this.voucherUrlsJson = voucherUrlsJson; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
}
