package com.zhongbang.controller.controller.staff;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.staff.dto.StaffPasswordRequest;
import com.zhongbang.controller.pojo.staff.dto.StaffSaveRequest;
import com.zhongbang.controller.pojo.staff.dto.StaffStatusRequest;
import com.zhongbang.controller.pojo.staff.vo.StaffMemberVo;
import com.zhongbang.controller.service.staff.StaffService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工管理。路径虽然在 {@code /api/admin/**} 里（员工令牌就能进别的管理接口），
 * 但类上的 {@code @PreAuthorize} 再要求角色是管理员。业务员和师傅登录后调用会得到 403。
 *
 * <p>{@code ref} 形如 {@code user-1} 或 {@code tech-88}，对应列表里的那一列。
 */
@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStaffController {

    private final StaffService staffService;

    public AdminStaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public ApiResponse<PageResult<StaffMemberVo>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(staffService.list(keyword, role, status, page, size));
    }

    @PostMapping
    public ApiResponse<StaffMemberVo> create(@RequestBody StaffSaveRequest request) {
        return ApiResponse.ok(staffService.create(request));
    }

    @PutMapping("/{ref}")
    public ApiResponse<StaffMemberVo> update(@PathVariable String ref, @RequestBody StaffSaveRequest request) {
        return ApiResponse.ok(staffService.update(ref, request));
    }

    @PostMapping("/{ref}/status")
    public ApiResponse<StaffMemberVo> status(@PathVariable String ref, @RequestBody StaffStatusRequest request) {
        String status = request == null ? null : request.getStatus();
        return ApiResponse.ok(staffService.changeStatus(ref, status));
    }

    @PostMapping("/{ref}/password")
    public ApiResponse<Void> password(@PathVariable String ref, @RequestBody StaffPasswordRequest request) {
        staffService.resetPassword(ref, request == null ? null : request.getPassword());
        return ApiResponse.ok();
    }
}
