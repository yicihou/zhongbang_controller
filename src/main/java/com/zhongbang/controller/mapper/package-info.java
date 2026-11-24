/**
 * 数据库访问层。Service 通过这里读写表，本层不决定业务规则。
 *
 * <p>每个接口标 {@code @Mapper}，继承 MyBatis-Plus 的 {@code BaseMapper}，一般不用手写 SQL。
 * 启动类的 {@code @MapperScan} 扫描本包及下面的业务子包，所以新 Mapper 放进对应业务子包就会被找到。
 * 表对应的 Java 对象放在 {@code pojo}，不放在这里。
 *
 * <p>客户、商品、订单、报修、收款、通知、师傅、工单都有 Mapper。
 * 报表子包仍只有说明文件。
 */
package com.zhongbang.controller.mapper;
