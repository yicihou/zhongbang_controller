package com.zhongbang.controller.pojo.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 安装或维修工单。派上门安装时插入 type=INSTALL 的一行，并记下订单行 id。
 */
@TableName("work_order")
public class WorkOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String workNo;
    private String type;
    private Long orderId;
    private Long orderItemId;
    private Long assetId;
    private Long customerId;
    private Long technicianId;
    private String status;
    private Long quoteAmount;
    private Long actualAmount;
    private LocalDateTime appointmentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getWorkNo() { return workNo; }
    public void setWorkNo(String workNo) { this.workNo = workNo; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getQuoteAmount() { return quoteAmount; }
    public void setQuoteAmount(Long quoteAmount) { this.quoteAmount = quoteAmount; }
    public Long getActualAmount() { return actualAmount; }
    public void setActualAmount(Long actualAmount) { this.actualAmount = actualAmount; }
    public LocalDateTime getAppointmentAt() { return appointmentAt; }
    public void setAppointmentAt(LocalDateTime appointmentAt) { this.appointmentAt = appointmentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
