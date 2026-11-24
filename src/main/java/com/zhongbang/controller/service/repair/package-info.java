/**
 * 报修规则和落库。
 * {@code RepairRules} 不访问数据库，负责报价、状态和维修费拆余款。
 * {@code RepairService} 查设备、占师傅档期、写通知，再把结果写回去。
 */
package com.zhongbang.controller.service.repair;
