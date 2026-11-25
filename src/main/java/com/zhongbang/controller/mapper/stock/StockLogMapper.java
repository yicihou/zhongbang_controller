package com.zhongbang.controller.mapper.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.stock.entity.StockLog;
import org.apache.ibatis.annotations.Mapper;

/** 库存流水。 */
@Mapper
public interface StockLogMapper extends BaseMapper<StockLog> {
}
