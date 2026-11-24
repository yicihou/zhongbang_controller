package com.zhongbang.controller.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhongbang.controller.common.enums.DeliveryMethod;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("order_item")
public class OrderItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long skuId;
    private String productName;
    private String skuName;
    private String coverImage;
    private Integer quantity;
    private Long unitPrice;
    private Long lineAmount;
    private DeliveryMethod deliveryMethod;
    private Long installFee;
    private LocalDate installDate;
    private String installSlot;
    private Long technicianId;
    private String addressSnapshotJson;
    private Long shipmentId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Long unitPrice) { this.unitPrice = unitPrice; }
    public Long getLineAmount() { return lineAmount; }
    public void setLineAmount(Long lineAmount) { this.lineAmount = lineAmount; }
    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }
    public Long getInstallFee() { return installFee; }
    public void setInstallFee(Long installFee) { this.installFee = installFee; }
    public LocalDate getInstallDate() { return installDate; }
    public void setInstallDate(LocalDate installDate) { this.installDate = installDate; }
    public String getInstallSlot() { return installSlot; }
    public void setInstallSlot(String installSlot) { this.installSlot = installSlot; }
    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
    public String getAddressSnapshotJson() { return addressSnapshotJson; }
    public void setAddressSnapshotJson(String addressSnapshotJson) { this.addressSnapshotJson = addressSnapshotJson; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
