package com.zhongbang.controller.pojo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 一次改价。表列名是 field_name，因为 field 容易和 SQL 关键字搅在一起。
 */
@TableName("price_log")
public class PriceLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private String scope;
    private Long orderItemId;
    private String fieldName;
    private Long beforeAmount;
    private Long afterAmount;
    private String reason;
    private String operatorName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public Long getBeforeAmount() { return beforeAmount; }
    public void setBeforeAmount(Long beforeAmount) { this.beforeAmount = beforeAmount; }
    public Long getAfterAmount() { return afterAmount; }
    public void setAfterAmount(Long afterAmount) { this.afterAmount = afterAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
