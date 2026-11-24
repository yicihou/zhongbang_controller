package com.zhongbang.controller.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.order.entity.Shipment;
import org.apache.ibatis.annotations.Mapper;

/** 发货记录。一行商品最多一条未取消的发货。 */
@Mapper
public interface ShipmentMapper extends BaseMapper<Shipment> {
}
