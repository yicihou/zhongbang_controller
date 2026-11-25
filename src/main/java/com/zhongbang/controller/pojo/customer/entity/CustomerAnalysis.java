package com.zhongbang.controller.pojo.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 一次分析结果。{@code customerId} 为空是全店分析。
 * promptText 是发给模型的摘要，里面的手机号和门牌已经打过码。
 */
@TableName("customer_analysis")
public class CustomerAnalysis {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long customerId;
    private String kind;
    private String promptText;
    private String resultText;
    private String operatorName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getPromptText() { return promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }
    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
