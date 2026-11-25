package com.zhongbang.controller.service.technician;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.mapper.order.OrderLineMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.technician.TechnicianMapper;
import com.zhongbang.controller.mapper.technician.TechnicianScheduleMapper;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.technician.entity.Technician;
import com.zhongbang.controller.pojo.technician.entity.TechnicianSchedule;
import com.zhongbang.controller.pojo.technician.vo.PendingInstallVo;
import com.zhongbang.controller.pojo.technician.vo.ScheduleVo;
import com.zhongbang.controller.pojo.technician.vo.TechnicianVo;
import com.zhongbang.controller.security.UserContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 师傅名单和档期。派工写档期在订单服务里，这里只负责查询。
 * 小程序选师傅和管理端排期页用的是同一张表。
 */
@Service
public class TechnicianService {

    private final TechnicianMapper technicianMapper;
    private final TechnicianScheduleMapper scheduleMapper;
    private final OrderLineMapper orderLineMapper;
    private final SalesOrderMapper salesOrderMapper;

    public TechnicianService(
            TechnicianMapper technicianMapper,
            TechnicianScheduleMapper scheduleMapper,
            OrderLineMapper orderLineMapper,
            SalesOrderMapper salesOrderMapper
    ) {
        this.technicianMapper = technicianMapper;
        this.scheduleMapper = scheduleMapper;
        this.orderLineMapper = orderLineMapper;
        this.salesOrderMapper = salesOrderMapper;
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

    /**
     * 还没定安装时间的上门安装行。排期页点师傅名字或某个格子时，老板从这里挑一单派进去，
     * 不用先跳到订单详情。只排除已取消的单子：没付清也能先派工，和订单详情的口径一致。
     */
    public List<PendingInstallVo> pendingInstalls() {
        UserContext.require();
        List<OrderLine> lines = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLine>()
                .eq(OrderLine::getDeliveryMethod, "ON_SITE_INSTALL")
                .isNull(OrderLine::getInstallDate)
                .orderByDesc(OrderLine::getId));
        if (lines.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = lines.stream().map(OrderLine::getOrderId).distinct().toList();
        Map<Long, SalesOrder> orders = new HashMap<>();
        for (SalesOrder order : salesOrderMapper.selectBatchIds(orderIds)) {
            orders.put(order.getId(), order);
        }
        List<PendingInstallVo> rows = new ArrayList<>();
        for (OrderLine line : lines) {
            SalesOrder order = orders.get(line.getOrderId());
            if (order == null || "CANCELLED".equals(order.getStatus())) {
                continue;
            }
            rows.add(new PendingInstallVo(
                    line.getId(),
                    line.getOrderId(),
                    order.getOrderNo(),
                    order.getCustomerName(),
                    line.getProductName(),
                    line.getSkuName(),
                    line.getQuantity() == null ? 0 : line.getQuantity(),
                    order.getAddressText()
            ));
        }
        return rows;
    }

    private TechnicianVo toVo(Technician row) {
        double rating = row.getRating() == null ? 5.0 : row.getRating().doubleValue();
        return new TechnicianVo(row.getId(), row.getName(), row.getMobile(), Jsons.splitComma(row.getSkills()), row.getRegion(), rating);
    }
}
