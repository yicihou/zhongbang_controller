package com.zhongbang.controller.pojo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 一行商品的发货记录。已发出或已派工之后，这张订单不能直接取消。
 */
@TableName("shipment")
public class Shipment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long orderItemId;
    private String method;
    private String status;
    private String carrier;
    private String trackingNo;
    private String logisticsCompany;
    private String freightNo;
    private String vehicleNo;
    private String courierName;
    private String courierMobile;
    private String appointAt;
    private String pickupCode;
    private Long pickupStoreId;
    private String pickedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
    public String getAppointAt() { return appointAt; }
    public void setAppointAt(String appointAt) { this.appointAt = appointAt; }
    public String getPickupCode() { return pickupCode; }
    public void setPickupCode(String pickupCode) { this.pickupCode = pickupCode; }
    public Long getPickupStoreId() { return pickupStoreId; }
    public void setPickupStoreId(Long pickupStoreId) { this.pickupStoreId = pickupStoreId; }
    public String getPickedAt() { return pickedAt; }
    public void setPickedAt(String pickedAt) { this.pickedAt = pickedAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
