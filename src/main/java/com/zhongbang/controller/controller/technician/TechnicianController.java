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
 * 派工本身在订单和报修接口里，不在这个类。
 *
 * <p>{@code GET /api/admin/technicians} 只列启用中的师傅，给报修和安装的下拉框用，
 * 和管理端员工管理不是同一个接口。管理员可以查，不必是老板。
 * 除了 id、姓名、手机，还带技能和区域，下拉文案和排期页要用。
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
