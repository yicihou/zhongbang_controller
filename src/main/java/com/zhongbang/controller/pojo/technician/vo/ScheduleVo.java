package com.zhongbang.controller.pojo.technician.vo;

/** 档期一格。used 是已占用人数，对应表里的 used_count。 */
public record ScheduleVo(
        Long id,
        Long technicianId,
        String serviceDate,
        String timeSlot,
        int capacity,
        int used,
        String orderNo,
        String customerName
) {
}
