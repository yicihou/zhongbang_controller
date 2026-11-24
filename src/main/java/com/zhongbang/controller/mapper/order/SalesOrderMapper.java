package com.zhongbang.controller.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import org.apache.ibatis.annotations.Mapper;

/** 订单头，表名是 {@code orders}。 */
@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {
}
