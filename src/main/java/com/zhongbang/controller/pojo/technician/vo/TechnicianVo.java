package com.zhongbang.controller.pojo.technician.vo;

import java.util.List;

/** 师傅。skills 是数组，管理端排期页直接 join。 */
public record TechnicianVo(
        Long id,
        String name,
        String mobile,
        List<String> skills,
        String region,
        double rating
) {
}
