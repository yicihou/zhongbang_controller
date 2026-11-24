package com.zhongbang.controller.mapper.repair;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.repair.entity.RepairLine;
import org.apache.ibatis.annotations.Mapper;

/** 报修行，表 {@code repair_line}。 */
@Mapper
public interface RepairLineMapper extends BaseMapper<RepairLine> {
}
