package com.zhongbang.controller.mapper.report;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.report.entity.MarginInsight;
import org.apache.ibatis.annotations.Mapper;

/** 毛利解读历史。数字由报表服务先算好，这里只保存模型和说明。 */
@Mapper
public interface MarginInsightMapper extends BaseMapper<MarginInsight> {
}
