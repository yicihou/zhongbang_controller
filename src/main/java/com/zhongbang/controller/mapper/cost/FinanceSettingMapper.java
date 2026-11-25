package com.zhongbang.controller.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.cost.entity.FinanceSetting;
import org.apache.ibatis.annotations.Mapper;

/** 全店进价和预警参数，只有 id = 1 这一行。 */
@Mapper
public interface FinanceSettingMapper extends BaseMapper<FinanceSetting> {
}
