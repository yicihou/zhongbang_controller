package com.zhongbang.controller.mapper.product;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/** {@code product} 表。增删改查由 MyBatis-Plus 生成，这里没有手写 SQL。 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
