package com.zhongbang.controller.pojo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 订单里的一行商品。{@code installFee} 为空表示上门安装费待定。
 * {@code shipment} 不落在这一张表，查出来后挂上，方便和发货进度一起看。
 */
@TableName("order_item")
public class OrderLine {

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
    private String deliveryMethod;
    private Long installFee;
    private String installDate;
    private String installSlot;
    private Long technicianId;
    private String technicianName;
    private String addressSnapshotJson;
    private Long shipmentId;
    private String workOrderNo;

    @TableField(exist = false)
    private Shipment shipment;

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
    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }
    public Long getInstallFee() { return installFee; }
    public void setInstallFee(Long installFee) { this.installFee = installFee; }
    public String getInstallDate() { return installDate; }
    public void setInstallDate(String installDate) { this.installDate = installDate; }
    public String getInstallSlot() { return installSlot; }
    public void setInstallSlot(String installSlot) { this.installSlot = installSlot; }
    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
    public String getTechnicianName() { return technicianName; }
    public void setTechnicianName(String technicianName) { this.technicianName = technicianName; }
    public String getAddressSnapshotJson() { return addressSnapshotJson; }
    public void setAddressSnapshotJson(String addressSnapshotJson) { this.addressSnapshotJson = addressSnapshotJson; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }
    public String getWorkOrderNo() { return workOrderNo; }
    public void setWorkOrderNo(String workOrderNo) { this.workOrderNo = workOrderNo; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment shipment) { this.shipment = shipment; }
}
