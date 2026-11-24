package com.zhongbang.controller.mapper.product;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * {@code product_sku} 表。扣库存用带条件的更新：库存不够时影响行数是 0，不会扣成负数。
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    @Update("""
            UPDATE product_sku
               SET stock = stock - #{qty}, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND stock >= #{qty} AND deleted = 0
            """)
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    @Update("""
            UPDATE product_sku
               SET stock = stock + #{qty}, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND deleted = 0
            """)
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);
}
