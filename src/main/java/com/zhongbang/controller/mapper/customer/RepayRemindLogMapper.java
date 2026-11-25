package com.zhongbang.controller.mapper.customer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.customer.entity.RepayRemindLog;
import org.apache.ibatis.annotations.Mapper;

/** 协议提醒去重。 */
@Mapper
public interface RepayRemindLogMapper extends BaseMapper<RepayRemindLog> {
}
