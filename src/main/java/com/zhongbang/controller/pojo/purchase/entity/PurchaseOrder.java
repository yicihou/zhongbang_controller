package com.zhongbang.controller.pojo.purchase.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一家供应商的采购单。批次首单的 ID 也是该批次的主单 ID。 */
@TableName("purchase_order")
public class PurchaseOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String purchaseNo;
    private Long batchId;
    private String clientToken;
    private Integer revision;
    private Long supplierId;
    private String orderDate;
    private String status;
    private Long freightAmount;
    private Long goodsAmount;
    private Long totalAmount;
    private Integer invoiced;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String invoiceType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer taxRate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer taxIncluded;
    private String attachmentUrls;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private String source;
    private String costMethod;
    private String operatorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPurchaseNo() { return purchaseNo; }
    public void setPurchaseNo(String purchaseNo) { this.purchaseNo = purchaseNo; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getClientToken() { return clientToken; }
    public void setClientToken(String clientToken) { this.clientToken = clientToken; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getFreightAmount() { return freightAmount; }
    public void setFreightAmount(Long freightAmount) { this.freightAmount = freightAmount; }
    public Long getGoodsAmount() { return goodsAmount; }
    public void setGoodsAmount(Long goodsAmount) { this.goodsAmount = goodsAmount; }
    public Long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Long totalAmount) { this.totalAmount = totalAmount; }
    public Integer getInvoiced() { return invoiced; }
    public void setInvoiced(Integer invoiced) { this.invoiced = invoiced; }
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    public Integer getTaxRate() { return taxRate; }
    public void setTaxRate(Integer taxRate) { this.taxRate = taxRate; }
    public Integer getTaxIncluded() { return taxIncluded; }
    public void setTaxIncluded(Integer taxIncluded) { this.taxIncluded = taxIncluded; }
    public String getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(String attachmentUrls) { this.attachmentUrls = attachmentUrls; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCostMethod() { return costMethod; }
    public void setCostMethod(String costMethod) { this.costMethod = costMethod; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
