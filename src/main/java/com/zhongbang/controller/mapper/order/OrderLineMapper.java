package com.zhongbang.controller.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import org.apache.ibatis.annotations.Mapper;

/** 订单行，表 {@code order_item}。 */
@Mapper
public interface OrderLineMapper extends BaseMapper<OrderLine> {
}
