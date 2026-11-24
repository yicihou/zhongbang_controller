package com.zhongbang.controller.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.module.customer.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
