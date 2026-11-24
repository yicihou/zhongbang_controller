package com.zhongbang.controller.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.module.customer.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code customer} 表的访问接口。{@code @Mapper} 告诉 MyBatis 为这个接口生成实现，
 * 启动类上的 {@code @MapperScan} 会扫到它。
 * {@code BaseMapper} 已提供 insert、selectOne、selectList 等，这里没有额外 SQL。
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
