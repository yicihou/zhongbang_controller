package com.zhongbang.controller.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.order.entity.PriceLog;
import org.apache.ibatis.annotations.Mapper;

/** 改价流水。 */
@Mapper
public interface PriceLogMapper extends BaseMapper<PriceLog> {
}
