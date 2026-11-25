package com.zhongbang.controller.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.cost.entity.CostPriceLog;
import org.apache.ibatis.annotations.Mapper;

/** 进价变动历史。 */
@Mapper
public interface CostPriceLogMapper extends BaseMapper<CostPriceLog> {
}
