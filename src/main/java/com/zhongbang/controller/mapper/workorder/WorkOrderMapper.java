package com.zhongbang.controller.mapper.workorder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.workorder.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;

/** 安装工单。派师傅时插入，工单号回写到订单行。 */
@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
}
