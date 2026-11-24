package com.zhongbang.controller.module.fulfillment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhongbang.controller.common.enums.DeliveryMethod;
import com.zhongbang.controller.common.enums.ShipmentStatus;

import java.time.LocalDateTime;

@TableName("shipment")
public class Shipment {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long orderItemId;
    @TableField("method")
    private DeliveryMethod method;
    private ShipmentStatus status;
    private String carrier;
    private String trackingNo;
    private String logisticsCompany;
    private String freightNo;
    private String vehicleNo;
    private String courierName;
    private String courierMobile;
    private LocalDateTime appointAt;
    private String pickupCode;
    private Long pickupStoreId;
    private LocalDateTime pickedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public DeliveryMethod getMethod() { return method; }
    public void setMethod(DeliveryMethod method) { this.method = method; }
    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public String getLogisticsCompany() { return logisticsCompany; }
    public void setLogisticsCompany(String logisticsCompany) { this.logisticsCompany = logisticsCompany; }
    public String getFreightNo() { return freightNo; }
    public void setFreightNo(String freightNo) { this.freightNo = freightNo; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public String getCourierName() { return courierName; }
    public void setCourierName(String courierName) { this.courierName = courierName; }
    public String getCourierMobile() { return courierMobile; }
    public void setCourierMobile(String courierMobile) { this.courierMobile = courierMobile; }
    public LocalDateTime getAppointAt() { return appointAt; }
    public void setAppointAt(LocalDateTime appointAt) { this.appointAt = appointAt; }
    public String getPickupCode() { return pickupCode; }
    public void setPickupCode(String pickupCode) { this.pickupCode = pickupCode; }
    public Long getPickupStoreId() { return pickupStoreId; }
    public void setPickupStoreId(Long pickupStoreId) { this.pickupStoreId = pickupStoreId; }
    public LocalDateTime getPickedAt() { return pickedAt; }
    public void setPickedAt(LocalDateTime pickedAt) { this.pickedAt = pickedAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
