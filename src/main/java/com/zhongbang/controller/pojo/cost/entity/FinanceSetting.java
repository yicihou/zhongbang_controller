package com.zhongbang.controller.pojo.cost.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 全店一份的进价算法和库存预警参数。表里永远用 id = 1 这一行。 */
@TableName("finance_setting")
public class FinanceSetting {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String costMethod;
    private Integer defaultMinStock;
    private Integer suggestMultiplier;
    private Integer hotTurnoverDays;
    private Integer slowTurnoverDays;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCostMethod() { return costMethod; }
    public void setCostMethod(String costMethod) { this.costMethod = costMethod; }
    public Integer getDefaultMinStock() { return defaultMinStock; }
    public void setDefaultMinStock(Integer defaultMinStock) { this.defaultMinStock = defaultMinStock; }
    public Integer getSuggestMultiplier() { return suggestMultiplier; }
    public void setSuggestMultiplier(Integer suggestMultiplier) { this.suggestMultiplier = suggestMultiplier; }
    public Integer getHotTurnoverDays() { return hotTurnoverDays; }
    public void setHotTurnoverDays(Integer hotTurnoverDays) { this.hotTurnoverDays = hotTurnoverDays; }
    public Integer getSlowTurnoverDays() { return slowTurnoverDays; }
    public void setSlowTurnoverDays(Integer slowTurnoverDays) { this.slowTurnoverDays = slowTurnoverDays; }
}
