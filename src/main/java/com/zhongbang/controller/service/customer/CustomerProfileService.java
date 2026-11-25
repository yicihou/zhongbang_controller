package com.zhongbang.controller.service.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.customer.CustomerAddressMapper;
import com.zhongbang.controller.mapper.customer.CustomerFollowMapper;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.mapper.customer.CustomerPreferenceMapper;
import com.zhongbang.controller.pojo.customer.dto.CustomerRequests;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.pojo.customer.entity.CustomerAddress;
import com.zhongbang.controller.pojo.customer.entity.CustomerFollow;
import com.zhongbang.controller.pojo.customer.entity.CustomerLevelRule;
import com.zhongbang.controller.pojo.customer.entity.CustomerPreference;
import com.zhongbang.controller.pojo.customer.vo.CustomerVo;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 老板手改客户资料：新建、改基本信息、地址、跟进、偏好和手动等级。
 * 电话下单、门店散客都从这里建档，不经过小程序登录。
 */
@Service
public class CustomerProfileService {

    private final CustomerMapper customerMapper;
    private final CustomerAddressMapper addressMapper;
    private final CustomerFollowMapper followMapper;
    private final CustomerPreferenceMapper preferenceMapper;
    private final CustomerLevelService levelService;
    private final CustomerQueryService queryService;

    public CustomerProfileService(
            CustomerMapper customerMapper,
            CustomerAddressMapper addressMapper,
            CustomerFollowMapper followMapper,
            CustomerPreferenceMapper preferenceMapper,
            CustomerLevelService levelService,
            CustomerQueryService queryService
    ) {
        this.customerMapper = customerMapper;
        this.addressMapper = addressMapper;
        this.followMapper = followMapper;
        this.preferenceMapper = preferenceMapper;
        this.levelService = levelService;
        this.queryService = queryService;
    }

    @Transactional
    public CustomerVo.Detail create(CustomerRequests.Profile request) {
        StaffRoles.requireBoss();
        Customer customer = new Customer();
        customer.setTenantId(1L);
        customer.setLevel(1);
        customer.setLevelCode("NORMAL");
        customer.setLevelManual(0);
        customer.setTotalPaid(0L);
        customer.setDeleted(0);
        customer.setCreatedAt(DateTimes.now());
        apply(customer, request, true);
        // 手建客户没有微信 openid。唯一索引不允许多个空值时（H2）会插不进去，所以写成 manual-手机号。
        customer.setOpenid("manual-" + customer.getMobile());
        customer.setUpdatedAt(DateTimes.now());
        customerMapper.insert(customer);
        return queryService.detail(customer.getId());
    }

    @Transactional
    public CustomerVo.Detail update(Long id, CustomerRequests.Profile request) {
        StaffRoles.requireBoss();
        Customer customer = queryService.requireCustomer(id);
        apply(customer, request, false);
        customer.setUpdatedAt(DateTimes.now());
        customerMapper.updateById(customer);
        return queryService.detail(id);
    }

    @Transactional
    public CustomerVo.Detail addAddress(Long id, CustomerRequests.Address request) {
        StaffRoles.requireBoss();
        Customer customer = queryService.requireCustomer(id);
        if (request == null || blank(request.detail()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写详细地址");
        }
        CustomerAddress row = new CustomerAddress();
        row.setCustomerId(customer.getId());
        fillAddress(row, request);
        row.setSource("MANUAL");
        row.setCreatedAt(DateTimes.now());
        if (row.getDefaultFlag() != null && row.getDefaultFlag() == 1) {
            clearDefault(customer.getId());
        }
        addressMapper.insert(row);
        if ((customer.getRegion() == null || customer.getRegion().isBlank()) && row.getRegion() != null) {
            customer.setRegion(row.getRegion());
            customer.setUpdatedAt(DateTimes.now());
            customerMapper.updateById(customer);
        }
        return queryService.detail(id);
    }

    @Transactional
    public CustomerVo.Detail updateAddress(Long id, Long addressId, CustomerRequests.Address request) {
        StaffRoles.requireBoss();
        queryService.requireCustomer(id);
        CustomerAddress row = requireAddress(id, addressId);
        fillAddress(row, request);
        if (row.getDefaultFlag() != null && row.getDefaultFlag() == 1) {
            clearDefault(id);
            row.setDefaultFlag(1);
        }
        addressMapper.updateById(row);
        return queryService.detail(id);
    }

    @Transactional
    public CustomerVo.Detail defaultAddress(Long id, Long addressId) {
        StaffRoles.requireBoss();
        queryService.requireCustomer(id);
        CustomerAddress row = requireAddress(id, addressId);
        clearDefault(id);
        row.setDefaultFlag(1);
        addressMapper.updateById(row);
        return queryService.detail(id);
    }

    @Transactional
    public CustomerVo.Detail addFollow(Long id, CustomerRequests.Follow request) {
        StaffRoles.requireBoss();
        queryService.requireCustomer(id);
        if (request == null || blank(request.content()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写跟进内容");
        }
        String content = request.content().trim();
        if (content.length() > 1000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "跟进内容不能超过 1000 字");
        }
        CustomerFollow row = new CustomerFollow();
        row.setCustomerId(id);
        row.setFollowDate(request.followDate() == null || request.followDate().isBlank()
                ? LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                : parseDate(request.followDate(), "跟进日期"));
        row.setContent(content);
        row.setNextDate(request.nextDate() == null || request.nextDate().isBlank() ? null : parseDate(request.nextDate(), "下次跟进日期"));
        row.setOperatorName(UserContext.require().getDisplayName());
        row.setCreatedAt(DateTimes.now());
        followMapper.insert(row);
        return queryService.detail(id);
    }

    @Transactional
    public CustomerVo.Detail savePreference(Long id, CustomerRequests.Preference request) {
        StaffRoles.requireBoss();
        queryService.requireCustomer(id);
        CustomerPreference row = preferenceMapper.selectById(id);
        boolean creating = row == null;
        if (creating) {
            row = new CustomerPreference();
            row.setCustomerId(id);
        }
        if (request == null) {
            request = new CustomerRequests.Preference(null, null, null, null, null, null);
        }
        row.setManualNote(limit(request.manualNote(), "补充说明", 1000));
        row.setManualBrand(limit(request.manualBrand(), "品牌", 64));
        row.setManualCategory(limit(request.manualCategory(), "品类", 64));
        row.setManualPriceBand(limit(request.manualPriceBand(), "价格区间", 32));
        row.setManualPayMethod(limit(request.manualPayMethod(), "付款方式", 32));
        row.setManualNeedInstall(request.manualNeedInstall() == null ? null : (request.manualNeedInstall() ? 1 : 0));
        row.setUpdatedAt(DateTimes.now());
        if (creating) {
            preferenceMapper.insert(row);
        } else {
            preferenceMapper.updateById(row);
        }
        return queryService.detail(id);
    }

    @Transactional
    public CustomerVo.Detail assignLevel(Long id, CustomerRequests.LevelAssign request) {
        StaffRoles.requireBoss();
        Customer customer = queryService.requireCustomer(id);
        boolean manual = request == null || request.manual() == null || request.manual();
        if (!manual) {
            customer.setLevelManual(0);
            customer.setUpdatedAt(DateTimes.now());
            customerMapper.updateById(customer);
            levelService.refreshAutoLevels();
            return queryService.detail(id);
        }
        CustomerLevelRule rule = levelService.require(request == null ? null : request.levelCode());
        customer.setLevelCode(rule.getCode());
        customer.setLevel(rule.getRankNo());
        customer.setLevelManual(1);
        customer.setUpdatedAt(DateTimes.now());
        customerMapper.updateById(customer);
        return queryService.detail(id);
    }

    private void apply(Customer customer, CustomerRequests.Profile request, boolean creating) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写客户资料");
        }
        customer.setName(requireText(request.name(), "姓名", 50));
        String mobile = requireText(request.mobile(), "手机号", 20);
        if (!mobile.matches("1\\d{10}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号应为 11 位数字");
        }
        assertMobileFree(mobile, customer.getId());
        customer.setMobile(mobile);
        customer.setAltMobile(limit(request.altMobile(), "备用电话", 20));
        customer.setWechat(limit(request.wechat(), "微信号", 64));
        String previousType = customer.getCustomerType() == null ? "PERSONAL" : customer.getCustomerType();
        customer.setCustomerType(CustomerRules.normalizeType(request.customerType(), creating ? "PERSONAL" : previousType));
        customer.setShopName(limit(request.shopName(), "厂名或店名", 128));
        if (request.machineCount() != null && request.machineCount() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "机器数量不能为负");
        }
        customer.setMachineCount(request.machineCount());
        customer.setMainProducts(limit(request.mainProducts(), "主营产品", 255));
        String previousSource = customer.getSource() == null ? "PHONE" : customer.getSource();
        String source = CustomerRules.normalizeSource(request.source(), creating ? "PHONE" : previousSource);
        if ("DEMO".equals(source) && !"DEMO".equals(previousSource)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能把客户标成演示数据");
        }
        customer.setSource(source);
        customer.setReferrer(limit(request.referrer(), "介绍人", 50));
        customer.setBirthday(request.birthday() == null || request.birthday().isBlank() ? null : parseDate(request.birthday(), "生日"));
        customer.setRemark(limit(request.remark(), "备注", 500));
        customer.setTags(CustomerRules.tags(request.tags()));
        customer.setRegion(limit(request.region(), "地区", 64));
    }

    private void fillAddress(CustomerAddress row, CustomerRequests.Address request) {
        String detail = requireText(request.detail(), "详细地址", 500);
        row.setDetail(detail);
        String region = blank(request.region());
        row.setRegion(region == null ? emptyToNull(CustomerRules.guessRegion(detail)) : limit(region, "地区", 64));
        row.setContactName(limit(request.contactName(), "联系人", 50));
        row.setContactMobile(limit(request.contactMobile(), "联系电话", 20));
        row.setDefaultFlag(Boolean.TRUE.equals(request.defaultAddress()) ? 1 : 0);
    }

    private void clearDefault(Long customerId) {
        List<CustomerAddress> rows = addressMapper.selectList(new LambdaQueryWrapper<CustomerAddress>()
                .eq(CustomerAddress::getCustomerId, customerId)
                .eq(CustomerAddress::getDefaultFlag, 1));
        for (CustomerAddress row : rows) {
            row.setDefaultFlag(0);
            addressMapper.updateById(row);
        }
    }

    private CustomerAddress requireAddress(Long customerId, Long addressId) {
        CustomerAddress row = addressMapper.selectById(addressId);
        if (row == null || !customerId.equals(row.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地址不存在");
        }
        return row;
    }

    private void assertMobileFree(String mobile, Long exceptId) {
        Long count = customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getMobile, mobile)
                .ne(exceptId != null, Customer::getId, exceptId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "这个手机号已经有客户档案");
        }
    }

    private static LocalDate parseDate(String raw, String label) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "请写成 yyyy-MM-dd");
        }
    }

    private static String requireText(String value, String label, int max) {
        String text = blank(value);
        if (text == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写" + label);
        }
        if (text.length() > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能超过 " + max + " 个字");
        }
        return text;
    }

    private static String limit(String value, String label, int max) {
        String text = blank(value);
        if (text != null && text.length() > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能超过 " + max + " 个字");
        }
        return text;
    }

    private static String blank(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
