package com.zhongbang.controller.pojo.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 老板手动补充的购买偏好。主键就是客户 id，一个客户一行。
 * 自动算出来的品牌、品类不写这张表，打开详情时现算。
 */
@TableName("customer_preference")
public class CustomerPreference {

    @TableId(type = IdType.INPUT)
    private Long customerId;
    private String manualNote;
    private String manualBrand;
    private String manualCategory;
    private String manualPriceBand;
    private String manualPayMethod;
    private Integer manualNeedInstall;
    private LocalDateTime updatedAt;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getManualNote() { return manualNote; }
    public void setManualNote(String manualNote) { this.manualNote = manualNote; }
    public String getManualBrand() { return manualBrand; }
    public void setManualBrand(String manualBrand) { this.manualBrand = manualBrand; }
    public String getManualCategory() { return manualCategory; }
    public void setManualCategory(String manualCategory) { this.manualCategory = manualCategory; }
    public String getManualPriceBand() { return manualPriceBand; }
    public void setManualPriceBand(String manualPriceBand) { this.manualPriceBand = manualPriceBand; }
    public String getManualPayMethod() { return manualPayMethod; }
    public void setManualPayMethod(String manualPayMethod) { this.manualPayMethod = manualPayMethod; }
    public Integer getManualNeedInstall() { return manualNeedInstall; }
    public void setManualNeedInstall(Integer manualNeedInstall) { this.manualNeedInstall = manualNeedInstall; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
