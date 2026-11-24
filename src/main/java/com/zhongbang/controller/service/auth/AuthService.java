package com.zhongbang.controller.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.adapter.wx.WxAuthAdapter;
import com.zhongbang.controller.pojo.auth.dto.LoginRequest;
import com.zhongbang.controller.pojo.auth.dto.LoginResponse;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.security.JwtService;
import com.zhongbang.controller.security.LoginUser;
import com.zhongbang.controller.security.UserType;
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
    private final JwtService jwtService;

    public AuthService(WxAuthAdapter wxAuthAdapter, CustomerMapper customerMapper, JwtService jwtService) {
        this.wxAuthAdapter = wxAuthAdapter;
        this.customerMapper = customerMapper;
        this.jwtService = jwtService;
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

    /** 足够长的手机号显示成 138****1234。短于 7 位或 null 则原样或空串，不做部分打码。 */
    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile == null ? "" : mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    /** 1 或 null：普通客户；2 银卡；3 金卡。其他数字也显示普通客户。等级没有自动升级逻辑。 */
    private static String levelText(Integer level) {
        if (level == null) {
            return "普通客户";
        }
        return switch (level) {
            case 2 -> "银卡客户";
            case 3 -> "金卡客户";
            default -> "普通客户";
        };
    }
}
