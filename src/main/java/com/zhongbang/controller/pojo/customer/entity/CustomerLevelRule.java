package com.zhongbang.controller.pojo.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 一条等级规则。累计实收达到 {@code minPaid}（分）就可以升到这一档。 */
@TableName("customer_level_rule")
public class CustomerLevelRule {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String code;
    private String name;
    private Long minPaid;
    private Integer rankNo;
    private Integer enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getMinPaid() { return minPaid; }
    public void setMinPaid(Long minPaid) { this.minPaid = minPaid; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
