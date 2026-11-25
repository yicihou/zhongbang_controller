package com.zhongbang.controller.pojo.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 供应商。应付和已付不在这张表上，记在 supplier_ledger。 */
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String contact;
    private String mobile;
    private String brands;
    private Integer accountDays;
    private String remark;
    private String source;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getBrands() { return brands; }
    public void setBrands(String brands) { this.brands = brands; }
    public Integer getAccountDays() { return accountDays; }
    public void setAccountDays(Integer accountDays) { this.accountDays = accountDays; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
