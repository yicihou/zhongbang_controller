package com.zhongbang.controller.mapper.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;

/** 采购单。 */
@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
}
