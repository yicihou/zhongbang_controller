package com.zhongbang.controller.mapper.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.stock.entity.StockAlertState;
import org.apache.ibatis.annotations.Mapper;

/** 规格是否已经处在库存预警中。 */
@Mapper
public interface StockAlertStateMapper extends BaseMapper<StockAlertState> {
}
