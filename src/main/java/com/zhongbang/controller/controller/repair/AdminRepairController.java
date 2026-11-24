package com.zhongbang.controller.controller.repair;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.order.dto.OrderRequests;
import com.zhongbang.controller.pojo.repair.dto.RepairRequests;
import com.zhongbang.controller.pojo.repair.vo.RepairVo;
import com.zhongbang.controller.service.repair.RepairService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端报修。要 STAFF 令牌。
 * 报价后保外会生成待付维修费，收款仍走 {@code /api/admin/payments/{id}/offline-confirm}。
 * 工作台的待报价、待派工直接用本列表的 status 筛选，没有单独的汇总接口。
 */
@RestController
@RequestMapping("/api/admin/repairs")
public class AdminRepairController {

    private final RepairService repairService;

    public AdminRepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @GetMapping
    public ApiResponse<PageResult<RepairVo.Admin>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(repairService.adminList(status, keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<RepairVo.Admin> detail(@PathVariable Long id) {
        return ApiResponse.ok(repairService.adminGet(id));
    }

    @PostMapping("/{id}/quote")
    public ApiResponse<RepairVo.Admin> quote(@PathVariable Long id, @RequestBody RepairRequests.Quote request) {
        return ApiResponse.ok(repairService.quote(id, request));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<RepairVo.Admin> assign(@PathVariable Long id, @RequestBody RepairRequests.Assign request) {
        return ApiResponse.ok(repairService.assign(id, request));
    }

    /** 每一行都派过师傅之后，才能把整张单标成完成。 */
    @PostMapping("/{id}/complete")
    public ApiResponse<RepairVo.Admin> complete(@PathVariable Long id) {
        return ApiResponse.ok(repairService.complete(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<RepairVo.Admin> cancel(@PathVariable Long id, @RequestBody OrderRequests.Cancel request) {
        return ApiResponse.ok(repairService.cancel(id, request));
    }
}
