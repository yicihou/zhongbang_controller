package com.zhongbang.controller.pojo.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 采购单里的一行。进价可以先空着，等老板补上再入库。 */
@TableName("purchase_item")
public class PurchaseItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long purchaseId;
    private Long skuId;
    private String productName;
    private String skuName;
    private Integer quantity;
    private Long unitCost;
    private Long lineAmount;
    private Long beforeCost;
    private Integer beforeStock;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long purchaseId) { this.purchaseId = purchaseId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getUnitCost() { return unitCost; }
    public void setUnitCost(Long unitCost) { this.unitCost = unitCost; }
    public Long getLineAmount() { return lineAmount; }
    public void setLineAmount(Long lineAmount) { this.lineAmount = lineAmount; }
    public Long getBeforeCost() { return beforeCost; }
    public void setBeforeCost(Long beforeCost) { this.beforeCost = beforeCost; }
    public Integer getBeforeStock() { return beforeStock; }
    public void setBeforeStock(Integer beforeStock) { this.beforeStock = beforeStock; }
}
