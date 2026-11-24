package com.zhongbang.controller.mapper.repair;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import org.apache.ibatis.annotations.Mapper;

/** 报修单头，表 {@code repair_ticket}。 */
@Mapper
public interface RepairTicketMapper extends BaseMapper<RepairTicket> {
}
