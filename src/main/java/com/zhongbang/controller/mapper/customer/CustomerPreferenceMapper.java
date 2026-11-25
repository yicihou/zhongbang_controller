package com.zhongbang.controller.mapper.customer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.customer.entity.CustomerPreference;
import org.apache.ibatis.annotations.Mapper;

/** 老板手填的购买偏好。主键是客户 id。 */
@Mapper
public interface CustomerPreferenceMapper extends BaseMapper<CustomerPreference> {
}
