package com.zhongbang.controller.pojo.cost.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一次进价修改。改前为空表示第一次填写，改后为空表示被清空。 */
@TableName("cost_price_log")
public class CostPriceLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long skuId;
    private Long beforePrice;
    private Long afterPrice;
    private String operatorName;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Long getBeforePrice() { return beforePrice; }
    public void setBeforePrice(Long beforePrice) { this.beforePrice = beforePrice; }
    public Long getAfterPrice() { return afterPrice; }
    public void setAfterPrice(Long afterPrice) { this.afterPrice = afterPrice; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
