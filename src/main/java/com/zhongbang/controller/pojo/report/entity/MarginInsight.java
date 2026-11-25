package com.zhongbang.controller.pojo.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一次毛利解读。numbersJson 是系统算好的数字，resultText 是模型写的中文，不是模型自己算的账。 */
@TableName("margin_insight")
public class MarginInsight {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String periodType;
    private String periodKey;
    private String invoiceScope;
    private String numbersJson;
    private String resultText;
    private String operatorName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public String getPeriodKey() { return periodKey; }
    public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }
    public String getInvoiceScope() { return invoiceScope; }
    public void setInvoiceScope(String invoiceScope) { this.invoiceScope = invoiceScope; }
    public String getNumbersJson() { return numbersJson; }
    public void setNumbersJson(String numbersJson) { this.numbersJson = numbersJson; }
    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
