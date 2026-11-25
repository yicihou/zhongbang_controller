package com.zhongbang.controller.pojo.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一次跟进。下次跟进日期可空。 */
@TableName("customer_follow")
public class CustomerFollow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long customerId;
    private LocalDate followDate;
    private String content;
    private LocalDate nextDate;
    private String operatorName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public LocalDate getFollowDate() { return followDate; }
    public void setFollowDate(LocalDate followDate) { this.followDate = followDate; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDate getNextDate() { return nextDate; }
    public void setNextDate(LocalDate nextDate) { this.nextDate = nextDate; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
