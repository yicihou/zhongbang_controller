package com.zhongbang.controller.mapper.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseItem;
import org.apache.ibatis.annotations.Mapper;

/** 采购明细。 */
@Mapper
public interface PurchaseItemMapper extends BaseMapper<PurchaseItem> {
}
