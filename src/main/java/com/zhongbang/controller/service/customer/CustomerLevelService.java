package com.zhongbang.controller.service.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.customer.CustomerLevelRuleMapper;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.pojo.customer.dto.CustomerRequests;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.pojo.customer.entity.CustomerLevelRule;
import com.zhongbang.controller.pojo.customer.vo.CustomerVo;
import com.zhongbang.controller.security.StaffRoles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 等级规则存在数据库里，页面上可以改名称和累计实收门槛。
 * 没被老板手动锁定的客户，按实收自动套用最高一档。等级只是标识，不算进价格。
 */
@Service
public class CustomerLevelService {

    private final CustomerLevelRuleMapper ruleMapper;
    private final CustomerMapper customerMapper;
    private final CustomerLedger ledger;

    public CustomerLevelService(CustomerLevelRuleMapper ruleMapper, CustomerMapper customerMapper, CustomerLedger ledger) {
        this.ruleMapper = ruleMapper;
        this.customerMapper = customerMapper;
        this.ledger = ledger;
    }

    public List<CustomerVo.LevelRule> list() {
        StaffRoles.requireBoss();
        return rules().stream().map(this::toVo).toList();
    }

    public List<CustomerLevelRule> rules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<CustomerLevelRule>().orderByAsc(CustomerLevelRule::getRankNo));
    }

    public CustomerLevelRule require(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择等级");
        }
        CustomerLevelRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<CustomerLevelRule>()
                .eq(CustomerLevelRule::getCode, code.trim().toUpperCase()));
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "等级不存在");
        }
        return rule;
    }

    public String nameOf(String code) {
        if (code == null) {
            return "普通";
        }
        return rules().stream()
                .filter(rule -> code.equals(rule.getCode()))
                .map(CustomerLevelRule::getName)
                .findFirst()
                .orElse(code);
    }

    @Transactional
    public CustomerVo.LevelRule update(Long id, CustomerRequests.LevelRule request) {
        StaffRoles.requireBoss();
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写等级规则");
        }
        CustomerLevelRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "等级不存在");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 32) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "等级名称需要 1 到 32 个字");
        }
        if (request.minPaid() == null || request.minPaid() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "累计实收门槛不能为负");
        }
        boolean enabled = request.enabled() == null || request.enabled();
        if ("NORMAL".equals(rule.getCode()) && !enabled) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "普通等级不能停用");
        }
        rule.setName(name);
        rule.setMinPaid(request.minPaid());
        rule.setEnabled(enabled ? 1 : 0);
        ruleMapper.updateById(rule);
        assertIncreasing();
        refreshAutoLevels();
        return toVo(rule);
    }

    /** 按当前规则，给没有手动锁定的客户重新套等级。 */
    @Transactional
    public void refreshAutoLevels() {
        List<CustomerLevelRule> enabled = rules().stream()
                .filter(rule -> rule.getEnabled() == null || rule.getEnabled() == 1)
                .sorted(Comparator.comparingInt(CustomerLevelRule::getRankNo).reversed())
                .toList();
        if (enabled.isEmpty()) {
            return;
        }
        CustomerLevelRule lowest = enabled.stream()
                .min(Comparator.comparingInt(CustomerLevelRule::getRankNo))
                .orElse(enabled.get(0));
        var received = ledger.load().receivedByCustomer();
        List<Customer> customers = customerMapper.selectList(new LambdaQueryWrapper<>());
        for (Customer customer : customers) {
            if (customer.getLevelManual() != null && customer.getLevelManual() == 1) {
                continue;
            }
            long paid = received.getOrDefault(customer.getId(), 0L);
            CustomerLevelRule match = enabled.stream()
                    .filter(rule -> paid >= (rule.getMinPaid() == null ? 0L : rule.getMinPaid()))
                    .findFirst()
                    .orElse(lowest);
            boolean same = match.getCode().equals(customer.getLevelCode())
                    && match.getRankNo().equals(customer.getLevel())
                    && Long.valueOf(paid).equals(customer.getTotalPaid() == null ? 0L : customer.getTotalPaid());
            if (same) {
                continue;
            }
            customer.setLevelCode(match.getCode());
            customer.setLevel(match.getRankNo());
            customer.setTotalPaid(paid);
            customer.setLevelManual(0);
            customer.setUpdatedAt(DateTimes.now());
            customerMapper.updateById(customer);
        }
    }

    private void assertIncreasing() {
        List<CustomerLevelRule> enabled = rules().stream()
                .filter(rule -> rule.getEnabled() != null && rule.getEnabled() == 1)
                .sorted(Comparator.comparingInt(CustomerLevelRule::getRankNo))
                .toList();
        long previous = -1;
        for (CustomerLevelRule rule : enabled) {
            long min = rule.getMinPaid() == null ? 0L : rule.getMinPaid();
            if (min < previous) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "更高的等级，累计实收门槛不能比低等级还低");
            }
            previous = min;
        }
    }

    private CustomerVo.LevelRule toVo(CustomerLevelRule rule) {
        return new CustomerVo.LevelRule(
                rule.getId(),
                rule.getCode(),
                rule.getName(),
                rule.getMinPaid() == null ? 0L : rule.getMinPaid(),
                rule.getRankNo() == null ? 0 : rule.getRankNo(),
                rule.getEnabled() == null || rule.getEnabled() == 1
        );
    }
}
