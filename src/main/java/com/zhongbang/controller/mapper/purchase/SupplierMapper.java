package com.zhongbang.controller.mapper.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.purchase.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;

/** 供应商。带 deleted 字段，查询会自动忽略已删除的行。 */
@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {
}
