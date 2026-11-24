/**
 * 启动时的装配：跨域、MyBatis-Plus 分页、以及 Redis / RabbitMQ 开或关时用哪套实现。
 *
 * <p>业务规则（能不能下单、库存够不够）不写在这个包。
 * 默认 {@code dev} 不连 Redis 和 RabbitMQ，用内存和空实现代替，本机一条命令就能启动。
 */
package com.zhongbang.controller.config;
