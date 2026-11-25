package com.zhongbang.controller.service.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.customer.CustomerAddressMapper;
import com.zhongbang.controller.mapper.customer.CustomerAssetMapper;
import com.zhongbang.controller.mapper.customer.CustomerFollowMapper;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.mapper.customer.CustomerPreferenceMapper;
import com.zhongbang.controller.mapper.customer.RepayAgreementMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.pojo.customer.entity.CustomerAsset;
import com.zhongbang.controller.pojo.customer.entity.CustomerAddress;
import com.zhongbang.controller.pojo.customer.entity.CustomerFollow;
import com.zhongbang.controller.pojo.customer.entity.CustomerPreference;
import com.zhongbang.controller.pojo.customer.entity.RepayAgreement;
import com.zhongbang.controller.pojo.customer.vo.CustomerVo;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.security.StaffRoles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 客户列表、详情和图表用的读接口。
 * 先按姓名、手机这些档案字段缩小范围，再把订单和报修的金额算好，最后排序、分页。
 */
@Service
public class CustomerQueryService {

    private final CustomerMapper customerMapper;
    private final CustomerAddressMapper addressMapper;
    private final CustomerAssetMapper assetMapper;
    private final CustomerFollowMapper followMapper;
    private final CustomerPreferenceMapper preferenceMapper;
    private final RepayAgreementMapper agreementMapper;
    private final SalesOrderMapper orderMapper;
    private final CustomerLedger ledger;
    private final CustomerLevelService levelService;
    private final RepayText repayText;

    public CustomerQueryService(
            CustomerMapper customerMapper,
            CustomerAddressMapper addressMapper,
            CustomerAssetMapper assetMapper,
            CustomerFollowMapper followMapper,
            CustomerPreferenceMapper preferenceMapper,
            RepayAgreementMapper agreementMapper,
            SalesOrderMapper orderMapper,
            CustomerLedger ledger,
            CustomerLevelService levelService,
            RepayText repayText
    ) {
        this.customerMapper = customerMapper;
        this.addressMapper = addressMapper;
        this.assetMapper = assetMapper;
        this.followMapper = followMapper;
        this.preferenceMapper = preferenceMapper;
        this.agreementMapper = agreementMapper;
        this.orderMapper = orderMapper;
        this.ledger = ledger;
        this.levelService = levelService;
        this.repayText = repayText;
    }

    @Transactional
    public PageResult<CustomerVo.ListItem> list(
            String name,
            String mobile,
            String region,
            String tag,
            String customerType,
            String level,
            boolean unpaidOnly,
            String sort,
            String direction,
            int page,
            int size
    ) {
        StaffRoles.requireBoss();
        levelService.refreshAutoLevels();
        List<CustomerVo.ListItem> rows = filtered(name, mobile, region, tag, customerType, level, unpaidOnly, sort, direction);
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        int from = Math.min((p - 1) * s, rows.size());
        int to = Math.min(from + s, rows.size());
        return PageResult.of(rows.subList(from, to), rows.size(), p, s);
    }

    /** 导出用，不分页。最多 5000 行，避免一次把内存撑满。 */
    @Transactional
    public List<CustomerVo.ListItem> exportRows(
            String name, String mobile, String region, String tag, String customerType, String level, boolean unpaidOnly, String sort, String direction
    ) {
        StaffRoles.requireBoss();
        levelService.refreshAutoLevels();
        List<CustomerVo.ListItem> rows = filtered(name, mobile, region, tag, customerType, level, unpaidOnly, sort, direction);
        return rows.size() > 5000 ? rows.subList(0, 5000) : rows;
    }

    @Transactional
    public CustomerVo.Detail detail(Long id) {
        StaffRoles.requireBoss();
        levelService.refreshAutoLevels();
        Customer customer = requireCustomer(id);
        CustomerLedger.Snapshot snap = ledger.load();
        importAddresses(customer, snap.ordersOf(id));
        CustomerLedger.Facts facts = snap.facts(id);
        CustomerPreference preference = preferenceMapper.selectById(id);
        CustomerLedger.PreferenceAuto auto = snap.preference(id);
        List<CustomerVo.Agreement> agreements = agreementMapper.selectList(new LambdaQueryWrapper<RepayAgreement>()
                        .eq(RepayAgreement::getCustomerId, id)
                        .orderByDesc(RepayAgreement::getCreatedAt))
                .stream()
                .map(row -> repayText.toVo(row, customer.getName()))
                .toList();
        return new CustomerVo.Detail(
                profile(customer),
                stats(facts),
                addresses(id),
                orders(snap, id),
                models(snap, id),
                assets(snap, id),
                repairs(snap, id),
                preference(auto, preference),
                follows(id),
                agreements,
                snap.overdue(id)
        );
    }

    @Transactional
    public CustomerVo.Overview overview() {
        StaffRoles.requireBoss();
        levelService.refreshAutoLevels();
        CustomerLedger.Snapshot snap = ledger.load();
        List<Customer> customers = customerMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Integer> levels = new HashMap<>();
        Map<String, Integer> regions = new HashMap<>();
        Map<String, long[]> modelSums = new HashMap<>();
        List<CustomerVo.NameAmount> unpaid = new ArrayList<>();
        int unpaidCustomers = 0;
        int overdue = 0;
        int sleeping = 0;
        long unpaidAmount = 0;
        for (Customer customer : customers) {
            String levelName = levelService.nameOf(customer.getLevelCode());
            levels.merge(levelName, 1, Integer::sum);
            String region = customer.getRegion() == null || customer.getRegion().isBlank() ? "未填地区" : customer.getRegion();
            regions.merge(region, 1, Integer::sum);
            CustomerLedger.Facts facts = snap.facts(customer.getId());
            if (facts.unpaidAmount() > 0) {
                unpaidCustomers++;
                unpaidAmount += facts.unpaidAmount();
                unpaid.add(new CustomerVo.NameAmount(customer.getName(), customer.getId(), 0, facts.unpaidAmount()));
            }
            if (snap.overdue(customer.getId())) {
                overdue++;
            }
            if (snap.sleeping(customer.getId(), customer.getCreatedAt())) {
                sleeping++;
            }
            for (CustomerLedger.ModelRow model : snap.models(customer.getId())) {
                long[] cell = modelSums.computeIfAbsent(model.model, key -> new long[2]);
                cell[0] += model.quantity;
                cell[1] += model.amount;
            }
        }
        unpaid.sort(Comparator.comparingLong(CustomerVo.NameAmount::amount).reversed());
        List<CustomerVo.NameAmount> models = modelSums.entrySet().stream()
                .map(entry -> new CustomerVo.NameAmount(entry.getKey(), null, entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(CustomerVo.NameAmount::amount).reversed())
                .limit(10)
                .toList();
        return new CustomerVo.Overview(
                topCounts(levels, 12),
                topCounts(regions, 12),
                models,
                unpaid.stream().limit(10).toList(),
                customers.size(),
                unpaidCustomers,
                overdue,
                sleeping,
                unpaidAmount
        );
    }

    public Customer requireCustomer(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "客户不存在");
        }
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "客户不存在");
        }
        return customer;
    }

    private List<CustomerVo.ListItem> filtered(
            String name, String mobile, String region, String tag, String customerType, String level,
            boolean unpaidOnly, String sort, String direction
    ) {
        String type = blank(customerType) == null ? null : CustomerRules.normalizeType(customerType, null);
        String levelCode = blank(level);
        CustomerLedger.Snapshot snap = ledger.load();
        List<SortRow> rows = new ArrayList<>();
        for (Customer customer : customerMapper.selectList(new LambdaQueryWrapper<>())) {
            if (!contains(customer.getName(), name)) continue;
            if (!contains(customer.getMobile(), mobile)) continue;
            if (!contains(customer.getRegion(), region)) continue;
            if (!contains(customer.getTags(), tag)) continue;
            if (type != null && !type.equals(customer.getCustomerType())) continue;
            if (levelCode != null && !levelCode.equalsIgnoreCase(customer.getLevelCode()) && !levelCode.equals(levelService.nameOf(customer.getLevelCode()))) {
                continue;
            }
            CustomerLedger.Facts facts = snap.facts(customer.getId());
            if (unpaidOnly && facts.unpaidAmount() <= 0) continue;
            rows.add(new SortRow(customer, facts, snap.overdue(customer.getId())));
        }
        Comparator<SortRow> comparator = comparator(sort, direction);
        rows.sort(comparator);
        return rows.stream().map(row -> toItem(row, levelService)).toList();
    }

    private void importAddresses(Customer customer, List<SalesOrder> orders) {
        List<CustomerAddress> existing = addressMapper.selectList(new LambdaQueryWrapper<CustomerAddress>()
                .eq(CustomerAddress::getCustomerId, customer.getId()));
        boolean anyDefault = existing.stream().anyMatch(row -> row.getDefaultFlag() != null && row.getDefaultFlag() == 1);
        boolean regionFilled = customer.getRegion() != null && !customer.getRegion().isBlank();
        for (SalesOrder order : orders) {
            String text = blank(order.getAddressText());
            if (text == null) continue;
            boolean duplicated = existing.stream().anyMatch(row -> text.equals(row.getDetail()));
            if (duplicated) continue;
            CustomerAddress row = new CustomerAddress();
            row.setCustomerId(customer.getId());
            row.setRegion(CustomerRules.guessRegion(text));
            row.setDetail(text.length() > 500 ? text.substring(0, 500) : text);
            row.setContactName(order.getCustomerName());
            row.setContactMobile(order.getCustomerMobile());
            row.setDefaultFlag(anyDefault ? 0 : 1);
            row.setSource("ORDER");
            row.setCreatedAt(DateTimes.now());
            addressMapper.insert(row);
            existing.add(row);
            anyDefault = true;
            if (!regionFilled) {
                customer.setRegion(row.getRegion());
                customer.setUpdatedAt(DateTimes.now());
                customerMapper.updateById(customer);
                regionFilled = true;
            }
        }
    }

    private List<CustomerVo.Address> addresses(Long customerId) {
        return addressMapper.selectList(new LambdaQueryWrapper<CustomerAddress>()
                        .eq(CustomerAddress::getCustomerId, customerId)
                        .orderByDesc(CustomerAddress::getDefaultFlag)
                        .orderByDesc(CustomerAddress::getCreatedAt))
                .stream()
                .map(row -> new CustomerVo.Address(
                        row.getId(),
                        row.getRegion(),
                        row.getDetail(),
                        row.getContactName(),
                        row.getContactMobile(),
                        row.getDefaultFlag() != null && row.getDefaultFlag() == 1,
                        row.getSource()
                ))
                .toList();
    }

    private List<CustomerVo.OrderBrief> orders(CustomerLedger.Snapshot snap, Long customerId) {
        return snap.ordersOf(customerId).stream()
                .sorted(Comparator.comparing(SalesOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(order -> new CustomerVo.OrderBrief(
                        order.getId(),
                        order.getOrderNo(),
                        order.getStatus(),
                        order.getPayableAmount() == null ? 0L : order.getPayableAmount(),
                        order.getPaidAmount() == null ? 0L : order.getPaidAmount(),
                        CustomerRules.cancelled(order.getStatus()) ? 0L : CustomerRules.unpaid(order.getPayableAmount(), order.getPaidAmount()),
                        DateTimes.format(order.getCreatedAt())
                ))
                .toList();
    }

    private List<CustomerVo.ModelStat> models(CustomerLedger.Snapshot snap, Long customerId) {
        return snap.models(customerId).stream()
                .map(row -> new CustomerVo.ModelStat(row.model, row.brand, row.category, row.times, row.quantity, row.amount, DateTimes.format(row.last)))
                .toList();
    }

    private List<CustomerVo.AssetBrief> assets(CustomerLedger.Snapshot snap, Long customerId) {
        return assetMapper.selectList(new LambdaQueryWrapper<CustomerAsset>().eq(CustomerAsset::getCustomerId, customerId))
                .stream()
                .map(asset -> new CustomerVo.AssetBrief(
                        asset.getId(),
                        asset.getBrand(),
                        asset.getModel(),
                        asset.getSn(),
                        asset.getSource() == null ? "INTERNAL" : asset.getSource(),
                        asset.getBuyDate() == null ? null : asset.getBuyDate().toString(),
                        asset.getWarrantyEnd() == null ? null : asset.getWarrantyEnd().toString(),
                        asset.getWarrantyEnd() != null && asset.getWarrantyEnd().isBefore(snap.today()),
                        snap.repairCount(asset.getId())
                ))
                .toList();
    }

    private List<CustomerVo.RepairBrief> repairs(CustomerLedger.Snapshot snap, Long customerId) {
        return snap.repairsOf(customerId).stream()
                .sorted(Comparator.comparing(RepairTicket::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ticket -> new CustomerVo.RepairBrief(
                        ticket.getId(),
                        ticket.getTicketNo(),
                        ticket.getStatus(),
                        ticket.getPayableAmount() == null ? 0L : ticket.getPayableAmount(),
                        ticket.getPaidAmount() == null ? 0L : ticket.getPaidAmount(),
                        CustomerRules.cancelled(ticket.getStatus()) ? 0L : CustomerRules.unpaid(ticket.getPayableAmount(), ticket.getPaidAmount()),
                        DateTimes.format(ticket.getCreatedAt())
                ))
                .toList();
    }

    private CustomerVo.Profile profile(Customer customer) {
        return new CustomerVo.Profile(
                customer.getId(),
                customer.getName(),
                customer.getMobile(),
                customer.getAltMobile(),
                customer.getWechat(),
                customer.getCustomerType() == null ? "PERSONAL" : customer.getCustomerType(),
                CustomerRules.typeLabel(customer.getCustomerType()),
                customer.getShopName(),
                customer.getMachineCount(),
                customer.getMainProducts(),
                customer.getSource(),
                CustomerRules.sourceLabel(customer.getSource()),
                customer.getReferrer(),
                customer.getBirthday() == null ? null : customer.getBirthday().toString(),
                customer.getRemark(),
                customer.getTags(),
                customer.getRegion(),
                customer.getLevel(),
                customer.getLevelCode() == null ? "NORMAL" : customer.getLevelCode(),
                levelService.nameOf(customer.getLevelCode()),
                customer.getLevelManual() != null && customer.getLevelManual() == 1,
                DateTimes.format(customer.getCreatedAt())
        );
    }

    private static CustomerVo.Stats stats(CustomerLedger.Facts facts) {
        return new CustomerVo.Stats(
                facts.orderAmount(),
                facts.receivedAmount(),
                facts.unpaidAmount(),
                facts.orderCount(),
                facts.avgAmount(),
                DateTimes.format(facts.firstOrderAt()),
                DateTimes.format(facts.lastOrderAt()),
                facts.daysSinceLast()
        );
    }

    private static CustomerVo.Preference preference(CustomerLedger.PreferenceAuto auto, CustomerPreference manual) {
        return new CustomerVo.Preference(
                auto.brand(),
                auto.category(),
                auto.priceBand(),
                auto.payMethod(),
                auto.needInstall(),
                manual == null ? null : manual.getManualNote(),
                manual == null ? null : manual.getManualBrand(),
                manual == null ? null : manual.getManualCategory(),
                manual == null ? null : manual.getManualPriceBand(),
                manual == null ? null : manual.getManualPayMethod(),
                manual == null || manual.getManualNeedInstall() == null ? null : manual.getManualNeedInstall() == 1
        );
    }

    private List<CustomerVo.Follow> follows(Long customerId) {
        return followMapper.selectList(new LambdaQueryWrapper<CustomerFollow>()
                        .eq(CustomerFollow::getCustomerId, customerId)
                        .orderByDesc(CustomerFollow::getFollowDate)
                        .orderByDesc(CustomerFollow::getCreatedAt))
                .stream()
                .map(row -> new CustomerVo.Follow(
                        row.getId(),
                        row.getFollowDate() == null ? null : row.getFollowDate().toString(),
                        row.getContent(),
                        row.getNextDate() == null ? null : row.getNextDate().toString(),
                        row.getOperatorName(),
                        DateTimes.format(row.getCreatedAt())
                ))
                .toList();
    }

    private static CustomerVo.ListItem toItem(SortRow row, CustomerLevelService levelService) {
        Customer customer = row.customer;
        return new CustomerVo.ListItem(
                customer.getId(),
                customer.getName(),
                customer.getMobile(),
                customer.getRegion(),
                customer.getCustomerType(),
                CustomerRules.typeLabel(customer.getCustomerType()),
                customer.getLevel(),
                customer.getLevelCode(),
                levelService.nameOf(customer.getLevelCode()),
                customer.getLevelManual() != null && customer.getLevelManual() == 1,
                customer.getTags(),
                row.facts.orderAmount(),
                row.facts.unpaidAmount(),
                row.facts.orderCount(),
                DateTimes.format(row.facts.lastOrderAt()),
                row.overdue
        );
    }

    private static Comparator<SortRow> comparator(String sort, String direction) {
        boolean asc = "asc".equalsIgnoreCase(direction);
        Comparator<SortRow> comparator = switch (blank(sort) == null ? "lastOrderAt" : sort.trim()) {
            case "orderAmount" -> Comparator.comparingLong(row -> row.facts.orderAmount());
            case "unpaidAmount" -> Comparator.comparingLong(row -> row.facts.unpaidAmount());
            case "orderCount" -> Comparator.comparingInt(row -> row.facts.orderCount());
            default -> Comparator.comparing(row -> row.facts.lastOrderAt(), Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        return asc ? comparator : comparator.reversed();
    }

    private static List<CustomerVo.NameCount> topCounts(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new CustomerVo.NameCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static boolean contains(String value, String keyword) {
        String key = blank(keyword);
        if (key == null) return true;
        return value != null && value.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT));
    }

    private static String blank(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private record SortRow(Customer customer, CustomerLedger.Facts facts, boolean overdue) {
    }

}
