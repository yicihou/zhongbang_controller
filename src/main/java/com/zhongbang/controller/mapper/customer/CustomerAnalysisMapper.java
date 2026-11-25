package com.zhongbang.controller.mapper.customer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.customer.entity.CustomerAnalysis;
import org.apache.ibatis.annotations.Mapper;

/** 客户分析和全店分析的历史。 */
@Mapper
public interface CustomerAnalysisMapper extends BaseMapper<CustomerAnalysis> {
}
