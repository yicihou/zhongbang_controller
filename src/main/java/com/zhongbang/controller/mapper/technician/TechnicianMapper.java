package com.zhongbang.controller.mapper.technician;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.technician.entity.Technician;
import org.apache.ibatis.annotations.Mapper;

/** 师傅档案。 */
@Mapper
public interface TechnicianMapper extends BaseMapper<Technician> {
}
