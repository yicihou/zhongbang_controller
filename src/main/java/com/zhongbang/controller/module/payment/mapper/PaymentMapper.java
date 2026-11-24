package com.zhongbang.controller.module.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.module.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
