package com.zhongbang.controller.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.adapter.wx.WxAuthAdapter;
import com.zhongbang.controller.module.auth.dto.LoginRequest;
import com.zhongbang.controller.module.auth.dto.LoginResponse;
import com.zhongbang.controller.module.customer.entity.Customer;
import com.zhongbang.controller.module.customer.mapper.CustomerMapper;
import com.zhongbang.controller.security.JwtService;
import com.zhongbang.controller.security.LoginUser;
import com.zhongbang.controller.security.UserType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile == null ? "" : mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

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
