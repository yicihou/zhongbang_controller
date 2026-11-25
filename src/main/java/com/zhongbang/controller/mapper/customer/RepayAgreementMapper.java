package com.zhongbang.controller.mapper.customer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.customer.entity.RepayAgreement;
import org.apache.ibatis.annotations.Mapper;

/** 协议还款。 */
@Mapper
public interface RepayAgreementMapper extends BaseMapper<RepayAgreement> {
}
