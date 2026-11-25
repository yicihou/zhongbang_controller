package com.zhongbang.controller.pojo.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 某天已经给某条协议发过某种提醒。用来防止定时任务重复推送。 */
@TableName("repay_remind_log")
public class RepayRemindLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long agreementId;
    private String remindOn;
    private String kind;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAgreementId() { return agreementId; }
    public void setAgreementId(Long agreementId) { this.agreementId = agreementId; }
    public String getRemindOn() { return remindOn; }
    public void setRemindOn(String remindOn) { this.remindOn = remindOn; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
