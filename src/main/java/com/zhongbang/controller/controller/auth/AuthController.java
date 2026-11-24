package com.zhongbang.controller.controller.auth;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.pojo.auth.dto.LoginRequest;
import com.zhongbang.controller.pojo.auth.dto.LoginResponse;
import com.zhongbang.controller.pojo.auth.dto.StaffLoginRequest;
import com.zhongbang.controller.pojo.auth.dto.StaffLoginResponse;
import com.zhongbang.controller.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户登录的 HTTP 入口。小程序 {@code api.login} 会 POST 到这里。
 *
 * <p>{@code @RestController} 表示：这是接口类，方法返回值直接变成 JSON，而不是跳转网页。
 * {@code @RequestMapping} 是类上的路径前缀。方法上的 {@code @PostMapping} 再拼一段。
 * {@code @RequestBody} 把 JSON 转成 {@code LoginRequest}；{@code @Valid} 触发字段上的校验
 * （code 不能空白），不通过则由全局异常处理返回 400 / 业务码 1001。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 公开接口。成功时 data 里是 token、过期秒数和客户摘要。 */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * 管理端员工登录，公开。成功时 data 里是 STAFF 令牌和角色。
     * 初始账号 admin / admin123 由 Flyway 写入，密码是 BCrypt 哈希。
     */
    @PostMapping("/staff/login")
    public ApiResponse<StaffLoginResponse> staffLogin(@Valid @RequestBody StaffLoginRequest request) {
        return ApiResponse.ok(authService.staffLogin(request));
    }

    /**
     * 占位。不读取请求体，不调用微信，直接返回业务码 1001 和固定文案。
     * 路径在安全配置里是公开的，所以未登录也能打到这句错误。
     */
    @PostMapping("/phone")
    public ApiResponse<Void> bindPhone() {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号绑定尚未实现，请后续接入微信能力");
    }
}
