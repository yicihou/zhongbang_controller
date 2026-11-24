package com.zhongbang.controller.mapper.payment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import org.apache.ibatis.annotations.Mapper;

/** 应付 / 收款，表 {@code payment}。 */
@Mapper
public interface PaymentBillMapper extends BaseMapper<PaymentBill> {
}
