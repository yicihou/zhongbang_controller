/**
 * 报修的 HTTP 入口。
 * 小程序用 {@code RepairController}（/api/repairs），管理端用 {@code AdminRepairController}（/api/admin/repairs）。
 * 这里只接收 JSON 并调用 {@code RepairService}。能不能报价、能不能派工，在 Service 里判断。
 */
package com.zhongbang.controller.controller.repair;
