package com.zhongbang.controller.mapper.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.purchase.entity.SupplierLedger;
import org.apache.ibatis.annotations.Mapper;

/** 供应商应付、已付和冲销。 */
@Mapper
public interface SupplierLedgerMapper extends BaseMapper<SupplierLedger> {
}
