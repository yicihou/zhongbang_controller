package com.zhongbang.controller.mapper.customer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.customer.entity.CustomerAddress;
import org.apache.ibatis.annotations.Mapper;

/** 客户地址簿。 */
@Mapper
public interface CustomerAddressMapper extends BaseMapper<CustomerAddress> {
}
