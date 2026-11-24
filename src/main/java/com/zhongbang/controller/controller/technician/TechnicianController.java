package com.zhongbang.controller.controller.technician;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.technician.vo.ScheduleVo;
import com.zhongbang.controller.pojo.technician.vo.TechnicianVo;
import com.zhongbang.controller.service.technician.TechnicianService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 师傅查询。小程序选师傅要登录；管理端名单和档期要 STAFF。
 * 派工本身在订单接口里，不在这个类。
 */
@RestController
public class TechnicianController {

    private final TechnicianService technicianService;

    public TechnicianController(TechnicianService technicianService) {
        this.technicianService = technicianService;
    }

    @GetMapping("/api/technicians/available")
    public ApiResponse<List<TechnicianVo>> available() {
        return ApiResponse.ok(technicianService.list());
    }

    @GetMapping("/api/admin/technicians")
    public ApiResponse<List<TechnicianVo>> adminList() {
        return ApiResponse.ok(technicianService.list());
    }

    @GetMapping("/api/admin/technicians/schedules")
    public ApiResponse<List<ScheduleVo>> schedules(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ApiResponse.ok(technicianService.schedules(from, to));
    }
}
