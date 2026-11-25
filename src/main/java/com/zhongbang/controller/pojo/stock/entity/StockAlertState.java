package com.zhongbang.controller.pojo.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 某个规格当前是不是已经在预警。
 * 低于最低库存时提醒一次；回到线以上之后再掉下去，才会再提醒。
 */
@TableName("stock_alert_state")
public class StockAlertState {

    @TableId(value = "sku_id", type = IdType.INPUT)
    private Long skuId;
    private Integer alerting;
    private LocalDateTime updatedAt;

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getAlerting() { return alerting; }
    public void setAlerting(Integer alerting) { this.alerting = alerting; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
