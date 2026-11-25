package com.zhongbang.controller.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.adapter.wx.WxAuthAdapter;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.mapper.auth.SysUserMapper;
import com.zhongbang.controller.pojo.auth.dto.LoginRequest;
import com.zhongbang.controller.pojo.auth.dto.LoginResponse;
import com.zhongbang.controller.pojo.auth.dto.StaffLoginRequest;
import com.zhongbang.controller.pojo.auth.dto.StaffLoginResponse;
import com.zhongbang.controller.pojo.auth.entity.SysUser;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.security.JwtService;
import com.zhongbang.controller.security.LoginUser;
import com.zhongbang.controller.security.UserType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 客户登录的业务步骤。被 {@code AuthController} 调用，自己调用微信适配、客户 Mapper 和 JWT。
 *
 * <p>{@code @Service} 把类放进容器。{@code @Transactional} 表示整个方法在一个数据库事务里：
 * 中途抛异常会回滚本次插入。只读查询不必加它；这里因为可能 insert，所以要写事务。
 */
@Service
public class AuthService {

    private final WxAuthAdapter wxAuthAdapter;
    private final CustomerMapper customerMapper;
    private final SysUserMapper sysUserMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            WxAuthAdapter wxAuthAdapter,
            CustomerMapper customerMapper,
            SysUserMapper sysUserMapper,
            JwtService jwtService,
            PasswordEncoder passwordEncoder
    ) {
        this.wxAuthAdapter = wxAuthAdapter;
        this.customerMapper = customerMapper;
        this.sysUserMapper = sysUserMapper;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用 code 换 openid（本地是 {@code dev_} 加 code），按 openid 找未删除的客户。
     * 找不到就插入一行：租户固定 1，等级 1，累计付款 0，来源 MINIAPP，没有姓名和手机号。
     * 然后签发 type=CUSTOMER 的 JWT。同一个 code 再次登录会命中已有行，不会重复插入
     * （openid 有唯一索引）。
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        WxAuthAdapter.WxSession session = wxAuthAdapter.exchangeCode(request.code());
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getOpenid, session.openid())
                .eq(Customer::getDeleted, 0)
                .last("LIMIT 1"));
        if (customer == null) {
            customer = new Customer();
            customer.setTenantId(1L);
            customer.setOpenid(session.openid());
            customer.setLevel(1);
            customer.setTotalPaid(0L);
            customer.setSource("MINIAPP");
            customer.setDeleted(0);
            customer.setCreatedAt(LocalDateTime.now());
            customer.setUpdatedAt(LocalDateTime.now());
            customerMapper.insert(customer);
        }
        LoginUser user = new LoginUser(customer.getId(), UserType.CUSTOMER, customer.getName());
        String token = jwtService.issue(user);
        boolean hasMobile = customer.getMobile() != null && !customer.getMobile().isBlank();
        return new LoginResponse(
                token,
                jwtService.expiresSeconds(),
                hasMobile,
                new LoginResponse.CustomerBrief(
                        customer.getId(),
                        customer.getName() == null ? "" : customer.getName(),
                        "",
                        maskMobile(customer.getMobile()),
                        customer.getLevel() == null ? 1 : customer.getLevel(),
                        levelText(customer.getLevel())
                )
        );
    }

    /**
     * 管理端账号登录。按用户名找未删除的员工，用 BCrypt 对密码。
     * 初始管理员是 Flyway 写入的 admin / admin123。
     * 令牌类型固定 STAFF，这样 {@code /api/admin/**} 的角色检查能通过；role 声明带上表里的 ADMIN 或 STAFF。
     */
    public StaffLoginResponse staffLogin(StaffLoginRequest request) {
        SysUser staff = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username().trim())
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));
        if (staff == null || staff.getPassword() == null || !passwordEncoder.matches(request.password(), staff.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if ("DISABLED".equalsIgnoreCase(staff.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        String name = staff.getName() == null || staff.getName().isBlank() ? staff.getUsername() : staff.getName();
        String roleName = staff.getRole() == null || staff.getRole().isBlank() ? "STAFF" : staff.getRole().trim();
        String role = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        LoginUser user = new LoginUser(staff.getId(), UserType.STAFF, name);
        String token = jwtService.issue(user, role);
        return new StaffLoginResponse(
                token,
                jwtService.expiresSeconds(),
                new StaffLoginResponse.StaffBrief(staff.getId(), name, "STAFF", role)
        );
    }

    /** 足够长的手机号显示成 138****1234。短于 7 位或 null 则原样或空串，不做部分打码。 */
    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile == null ? "" : mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    /** 1 或 null：普通客户；2 银卡；3 金卡；4 VIP。自动升级在客户模块里，不在登录时计算。 */
    private static String levelText(Integer level) {
        if (level == null) {
            return "普通客户";
        }
        return switch (level) {
            case 2 -> "银卡客户";
            case 3 -> "金卡客户";
            case 4 -> "VIP客户";
            default -> "普通客户";
        };
    }
}
