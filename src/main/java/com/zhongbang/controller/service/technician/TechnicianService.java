package com.zhongbang.controller.service.technician;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.mapper.technician.TechnicianMapper;
import com.zhongbang.controller.mapper.technician.TechnicianScheduleMapper;
import com.zhongbang.controller.pojo.technician.entity.Technician;
import com.zhongbang.controller.pojo.technician.entity.TechnicianSchedule;
import com.zhongbang.controller.pojo.technician.vo.ScheduleVo;
import com.zhongbang.controller.pojo.technician.vo.TechnicianVo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 师傅名单和档期。派工写档期在订单服务里，这里只负责查询。
 * 小程序选师傅和管理端排期页用的是同一张表。
 */
@Service
public class TechnicianService {

    private final TechnicianMapper technicianMapper;
    private final TechnicianScheduleMapper scheduleMapper;

    public TechnicianService(TechnicianMapper technicianMapper, TechnicianScheduleMapper scheduleMapper) {
        this.technicianMapper = technicianMapper;
        this.scheduleMapper = scheduleMapper;
    }

    /** 只返回启用中的师傅。报修派工、安装派工和小程序选师傅都用这份名单。 */
    public List<TechnicianVo> list() {
        return technicianMapper.selectList(new LambdaQueryWrapper<Technician>().orderByAsc(Technician::getId))
                .stream()
                .filter(TechnicianRules::enabled)
                .map(this::toVo)
                .toList();
    }

    public List<ScheduleVo> schedules(String from, String to) {
        LambdaQueryWrapper<TechnicianSchedule> wrapper = new LambdaQueryWrapper<TechnicianSchedule>()
                .orderByAsc(TechnicianSchedule::getServiceDate)
                .orderByAsc(TechnicianSchedule::getTimeSlot);
        if (from != null && !from.isBlank()) {
            wrapper.ge(TechnicianSchedule::getServiceDate, from.trim());
        }
        if (to != null && !to.isBlank()) {
            wrapper.le(TechnicianSchedule::getServiceDate, to.trim());
        }
        return scheduleMapper.selectList(wrapper).stream().map(row -> new ScheduleVo(
                row.getId(),
                row.getTechnicianId(),
                row.getServiceDate(),
                row.getTimeSlot(),
                row.getCapacity() == null ? 0 : row.getCapacity(),
                row.getUsedCount() == null ? 0 : row.getUsedCount(),
                row.getOrderNo(),
                row.getCustomerName()
        )).toList();
    }

    private TechnicianVo toVo(Technician row) {
        double rating = row.getRating() == null ? 5.0 : row.getRating().doubleValue();
        return new TechnicianVo(row.getId(), row.getName(), row.getMobile(), Jsons.splitComma(row.getSkills()), row.getRegion(), rating);
    }
}
