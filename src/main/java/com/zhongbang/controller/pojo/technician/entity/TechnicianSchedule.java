package com.zhongbang.controller.pojo.technician.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 师傅某一天某个时段的档期。已用满（usedCount 达到 capacity）时再派工会报 3002。
 */
@TableName("technician_schedule")
public class TechnicianSchedule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long technicianId;
    private String serviceDate;
    private String timeSlot;
    private Integer capacity;
    private Integer usedCount;
    private String orderNo;
    private String customerName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
    public String getServiceDate() { return serviceDate; }
    public void setServiceDate(String serviceDate) { this.serviceDate = serviceDate; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
}
