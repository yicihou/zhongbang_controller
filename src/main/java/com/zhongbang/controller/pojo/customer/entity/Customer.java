package com.zhongbang.controller.pojo.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户表一行。登录时按 openid 查找或插入。
 *
 * <p>{@code @TableName} 指定表名。{@code @TableId(type = ASSIGN_ID)} 表示主键不靠数据库自增，
 * 插入前由 MyBatis-Plus 生成雪花 id（一个很长的 long）。
 * 全局配置把名为 deleted 的字段当作逻辑删除：0 未删，1 已删。本类有这个字段。
 *
 * <p>V5 起补了备用电话、客户类型、地区等老板才看的资料。
 * {@code level} 仍是 1 普通、2 银卡、3 金卡、4 VIP。{@code levelManual} 为 1 时不按累计实收自动升级。
 */
@TableName("customer")
public class Customer {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String openid;
    private String unionid;
    private String mobile;
    private String name;
    private Integer level;
    private String tags;
    private Long totalPaid;
    private String source;
    private String altMobile;
    private String wechat;
    private String customerType;
    private String shopName;
    private Integer machineCount;
    private String mainProducts;
    private String referrer;
    private LocalDate birthday;
    private String remark;
    private String region;
    private String levelCode;
    private Integer levelManual;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public Long getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Long totalPaid) { this.totalPaid = totalPaid; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getAltMobile() { return altMobile; }
    public void setAltMobile(String altMobile) { this.altMobile = altMobile; }
    public String getWechat() { return wechat; }
    public void setWechat(String wechat) { this.wechat = wechat; }
    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public Integer getMachineCount() { return machineCount; }
    public void setMachineCount(Integer machineCount) { this.machineCount = machineCount; }
    public String getMainProducts() { return mainProducts; }
    public void setMainProducts(String mainProducts) { this.mainProducts = mainProducts; }
    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getLevelCode() { return levelCode; }
    public void setLevelCode(String levelCode) { this.levelCode = levelCode; }
    public Integer getLevelManual() { return levelManual; }
    public void setLevelManual(Integer levelManual) { this.levelManual = levelManual; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
