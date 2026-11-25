package com.zhongbang.controller.mapper.customer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.customer.entity.CustomerFollow;
import org.apache.ibatis.annotations.Mapper;

/** 客户跟进记录。 */
@Mapper
public interface CustomerFollowMapper extends BaseMapper<CustomerFollow> {
}
