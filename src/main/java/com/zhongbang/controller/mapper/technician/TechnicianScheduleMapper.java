package com.zhongbang.controller.mapper.technician;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.technician.entity.TechnicianSchedule;
import org.apache.ibatis.annotations.Mapper;

/** 师傅档期。同一人、同一天、同一时段只有一行。 */
@Mapper
public interface TechnicianScheduleMapper extends BaseMapper<TechnicianSchedule> {
}
