package com.zhongbang.controller.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.module.order.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
