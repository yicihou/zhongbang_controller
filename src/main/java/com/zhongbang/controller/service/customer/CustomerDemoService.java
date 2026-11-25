package com.zhongbang.controller.service.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.config.ZhongbangProperties;
import com.zhongbang.controller.mapper.customer.CustomerAddressMapper;
import com.zhongbang.controller.mapper.customer.CustomerAssetMapper;
import com.zhongbang.controller.mapper.customer.CustomerAnalysisMapper;
import com.zhongbang.controller.mapper.customer.CustomerFollowMapper;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.mapper.customer.CustomerPreferenceMapper;
import com.zhongbang.controller.mapper.customer.RepayAgreementLogMapper;
import com.zhongbang.controller.mapper.customer.RepayAgreementMapper;
import com.zhongbang.controller.mapper.customer.RepayRemindLogMapper;
import com.zhongbang.controller.mapper.notice.AdminNoticeMapper;
import com.zhongbang.controller.mapper.order.OrderLineMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.mapper.repair.RepairLineMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.pojo.customer.entity.CustomerAddress;
import com.zhongbang.controller.pojo.customer.entity.CustomerAsset;
import com.zhongbang.controller.pojo.customer.entity.CustomerAnalysis;
import com.zhongbang.controller.pojo.customer.entity.CustomerFollow;
import com.zhongbang.controller.pojo.customer.entity.CustomerPreference;
import com.zhongbang.controller.pojo.customer.entity.RepayAgreement;
import com.zhongbang.controller.pojo.customer.entity.RepayAgreementLog;
import com.zhongbang.controller.pojo.customer.entity.RepayRemindLog;
import com.zhongbang.controller.pojo.notice.entity.AdminNotice;
import com.zhongbang.controller.pojo.customer.vo.CustomerVo;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.repair.entity.RepairLine;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.security.StaffRoles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 开发环境用的演示客户。生产配置里开关是关的，调用会直接拒绝。
 * 再点一次不会重复插入。清除也只删 source=DEMO 的客户及其订单、报修和协议。
 */
@Service
public class CustomerDemoService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /**
     * 列：姓名|类型|地区|店名|机器数|主营|标签|等级|是否手动|应付分|已付分|商品名|规格id|配送|是否报修|协议|订单距今天数。
     * 应付为 0 表示这个客户还没下过单。
     */
    private static final String[] ROWS = {
            "沈建华|FACTORY|杭州市西湖区|建华服饰|40|T恤|月结,老客户|GOLD|1|656000|400000|电脑平缝机 A5|1011|ON_SITE_INSTALL|1|OVERDUE|12",
            "周丽敏|WORKSHOP|杭州市滨江区|丽敏加工|8|童装|加急|SILVER|0|328000|200000|电脑平缝机 A5|1011|ON_SITE_INSTALL|0|TODAY|20",
            "马国强|DEALER|宁波市鄞州区|国强缝纫机|15|整机批发|同行|VIP|1|690000|690000|四线包缝机 E4|1031|FREIGHT|0|SOON|6",
            "陈小燕|SCHOOL|温州市鹿城区|小燕培训|6|教学|学期采购|NORMAL|0|158000|158000|家用多功能机 M3|1021|STORE_PICKUP|0|DONE|40",
            "吴海波|FACTORY|嘉兴市南湖区|海波针织|60|针织衫|大客户|VIP|0|6800000|5000000|电脑绣花机 12 头|1041|ON_SITE_INSTALL|1|OVERDUE|3",
            "郑美玲|PERSONAL|绍兴市越城区||0|家用|新客|NORMAL|0|158000|0|家用多功能机 M3|1021|EXPRESS|0|NONE|2",
            "孙志远|WORKSHOP|金华市婺城区|志远牛仔|12|牛仔|厚料|SILVER|0|336000|336000|电脑平缝机 A5|1012|ON_SITE_INSTALL|0|NONE|30",
            "钱芳|FACTORY|苏州市吴中区|芳华制衣|25|衬衫|季结|GOLD|0|690000|100000|四线包缝机 E4|1031|ON_SITE_INSTALL|1|SOON|15",
            "冯磊|DEALER|南京市江宁区|磊达机电|9|配件|现款|SILVER|0|328000|328000|电脑平缝机 A5|1011|FREIGHT|0|NONE|50",
            "褚雪|SCHOOL|合肥市包河区|雪松职校|20|实训|招投标|GOLD|1|656000|656000|电脑平缝机 A5|1011|ON_SITE_INSTALL|0|NONE|8",
            "卫东|FACTORY|杭州市余杭区|东城服装|30|工装|政府单|GOLD|0|1380000|800000|四线包缝机 E4|1031|ON_SITE_INSTALL|0|TODAY|18",
            "蒋婷|WORKSHOP|宁波市海曙区|婷婷绣坊|4|绣花|小单|NORMAL|0|158000|50000|家用多功能机 M3|1021|EXPRESS|1|OVERDUE|70",
            "韩冰|PERSONAL|温州市瓯海区||0|家用||NORMAL|0|0|0|家用多功能机 M3|1021|EXPRESS|0|NONE|0",
            "杨帆|FACTORY|嘉兴市桐乡市|帆顺毛衫|18|毛衣|旺季|SILVER|0|328000|328000|电脑平缝机 A5|1011|ON_SITE_INSTALL|0|NONE|90",
            "秦朗|DEALER|绍兴市柯桥区|朗润批发|22|面辅料|账期|VIP|0|690000|400000|四线包缝机 E4|1031|FREIGHT|0|SOON|11",
            "许静|SCHOOL|金华市义乌市|静雅培训班|5|短训||NORMAL|0|316000|316000|家用多功能机 M3|1021|STORE_PICKUP|0|NONE|25",
            "何勇|FACTORY|苏州市常熟市|勇盛羽绒服|35|羽绒|冬季|GOLD|0|680000|200000|电脑绣花机 12 头|1041|ON_SITE_INSTALL|1|OVERDUE|9",
            "吕倩|WORKSHOP|南京市鼓楼区|倩影改衣|3|修改||NORMAL|0|328000|0|电脑平缝机 A5|1011|ON_SITE_INSTALL|0|NONE|4",
            "施杰|PERSONAL|合肥市蜀山区||0|||NORMAL|0|0|0|家用多功能机 M3|1021|EXPRESS|0|NONE|0",
            "孔霞|FACTORY|杭州市萧山区|霞光童装|16|童装|复购|SILVER|0|328000|328000|电脑平缝机 A5|1011|EXPRESS|0|NONE|200",
            "曹敏|WORKSHOP|宁波市江北区|敏敏家纺|7|家纺|窗帘|SILVER|0|690000|690000|四线包缝机 E4|1031|ON_SITE_INSTALL|0|NONE|210",
            "严浩|DEALER|温州市龙湾区|浩盛贸易|11|出口|美元结算|GOLD|1|1380000|1380000|电脑平缝机 A5|1011|FREIGHT|0|DONE|14",
            "华莉|SCHOOL|嘉兴市海宁市|莉雅技校|14|教学|两年一换|SILVER|0|474000|474000|家用多功能机 M3|1021|ON_SITE_INSTALL|0|NONE|60",
            "金鑫|FACTORY|绍兴市上虞区|鑫达工装|28|工装|招标|GOLD|0|656000|300000|电脑平缝机 A5|1012|ON_SITE_INSTALL|1|TODAY|1",
            "魏娜|PERSONAL|金华市东阳市||1|窗帘||NORMAL|0|158000|158000|家用多功能机 M3|1021|EXPRESS|0|NONE|220",
            "陶军|WORKSHOP|苏州市昆山市|军哥皮具|6|皮具|厚料|SILVER|0|336000|100000|电脑平缝机 A5|1012|ON_SITE_INSTALL|0|SOON|16",
            "姜丽|FACTORY|南京市栖霞区|丽都时装|21|时装|当季|GOLD|0|690000|690000|四线包缝机 E4|1031|ON_SITE_INSTALL|0|NONE|45",
            "戚伟|DEALER|合肥市庐阳区|伟业配件|8|零件|现结|NORMAL|0|9000|9000|旋梭组件|502|EXPRESS|0|NONE|33",
            "谢婷婷|SCHOOL|杭州市拱墅区|婷婷工作室|2|兴趣班||NORMAL|0|0|0|家用多功能机 M3|1021|EXPRESS|0|NONE|0",
            "邹凯|FACTORY|宁波市镇海区|凯旋制服|19|制服|年框|SILVER|0|328000|150000|电脑平缝机 A5|1011|ON_SITE_INSTALL|0|NONE|27"
    };

    private final ZhongbangProperties properties;
    private final CustomerMapper customerMapper;
    private final CustomerAddressMapper addressMapper;
    private final CustomerFollowMapper followMapper;
    private final CustomerPreferenceMapper preferenceMapper;
    private final CustomerAnalysisMapper analysisMapper;
    private final CustomerAssetMapper assetMapper;
    private final SalesOrderMapper orderMapper;
    private final OrderLineMapper orderLineMapper;
    private final PaymentBillMapper paymentBillMapper;
    private final RepairTicketMapper repairTicketMapper;
    private final RepairLineMapper repairLineMapper;
    private final RepayAgreementMapper agreementMapper;
    private final RepayAgreementLogMapper agreementLogMapper;
    private final RepayRemindLogMapper remindLogMapper;
    private final AdminNoticeMapper noticeMapper;
    private final CustomerLevelService levelService;

    public CustomerDemoService(
            ZhongbangProperties properties,
            CustomerMapper customerMapper,
            CustomerAddressMapper addressMapper,
            CustomerFollowMapper followMapper,
            CustomerPreferenceMapper preferenceMapper,
            CustomerAnalysisMapper analysisMapper,
            CustomerAssetMapper assetMapper,
            SalesOrderMapper orderMapper,
            OrderLineMapper orderLineMapper,
            PaymentBillMapper paymentBillMapper,
            RepairTicketMapper repairTicketMapper,
            RepairLineMapper repairLineMapper,
            RepayAgreementMapper agreementMapper,
            RepayAgreementLogMapper agreementLogMapper,
            RepayRemindLogMapper remindLogMapper,
            AdminNoticeMapper noticeMapper,
            CustomerLevelService levelService
    ) {
        this.properties = properties;
        this.customerMapper = customerMapper;
        this.addressMapper = addressMapper;
        this.followMapper = followMapper;
        this.preferenceMapper = preferenceMapper;
        this.analysisMapper = analysisMapper;
        this.assetMapper = assetMapper;
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.paymentBillMapper = paymentBillMapper;
        this.repairTicketMapper = repairTicketMapper;
        this.repairLineMapper = repairLineMapper;
        this.agreementMapper = agreementMapper;
        this.agreementLogMapper = agreementLogMapper;
        this.remindLogMapper = remindLogMapper;
        this.noticeMapper = noticeMapper;
        this.levelService = levelService;
    }

    @Transactional
    public CustomerVo.DemoResult seed() {
        StaffRoles.requireBoss();
        requireEnabled();
        return seedInternal();
    }

    /** 仅供 dev 且打开了 auto-seed 时在启动阶段调用，没有登录用户。 */
    @Transactional
    public CustomerVo.DemoResult seedInternal() {
        requireEnabled();
        Long existing = customerMapper.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getSource, "DEMO"));
        if (existing != null && existing > 0) {
            return new CustomerVo.DemoResult(false, existing.intValue(), "已经有演示客户，没有重复生成");
        }
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDateTime now = DateTimes.now();
        for (int i = 0; i < ROWS.length; i++) {
            insertOne(ROWS[i], i, today, now);
        }
        levelService.refreshAutoLevels();
        return new CustomerVo.DemoResult(true, ROWS.length, "已生成 " + ROWS.length + " 位演示客户");
    }

    @Transactional
    public CustomerVo.DemoResult clear() {
        StaffRoles.requireBoss();
        requireEnabled();
        List<Customer> customers = customerMapper.selectList(new LambdaQueryWrapper<Customer>().eq(Customer::getSource, "DEMO"));
        if (customers.isEmpty()) {
            return new CustomerVo.DemoResult(false, 0, "没有演示客户");
        }
        List<Long> ids = customers.stream().map(Customer::getId).toList();
        List<SalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>().in(SalesOrder::getCustomerId, ids));
        List<Long> orderIds = orders.stream().map(SalesOrder::getId).toList();
        List<RepairTicket> tickets = repairTicketMapper.selectList(new LambdaQueryWrapper<RepairTicket>().in(RepairTicket::getCustomerId, ids));
        List<Long> ticketIds = tickets.stream().map(RepairTicket::getId).toList();
        if (!orderIds.isEmpty()) {
            paymentBillMapper.delete(new LambdaQueryWrapper<PaymentBill>().in(PaymentBill::getOrderId, orderIds));
            orderLineMapper.delete(new LambdaQueryWrapper<OrderLine>().in(OrderLine::getOrderId, orderIds));
            orderMapper.delete(new LambdaQueryWrapper<SalesOrder>().in(SalesOrder::getId, orderIds));
        }
        if (!ticketIds.isEmpty()) {
            paymentBillMapper.delete(new LambdaQueryWrapper<PaymentBill>().in(PaymentBill::getBizId, ticketIds).eq(PaymentBill::getBizType, "REPAIR"));
            repairLineMapper.delete(new LambdaQueryWrapper<RepairLine>().in(RepairLine::getTicketId, ticketIds));
            repairTicketMapper.delete(new LambdaQueryWrapper<RepairTicket>().in(RepairTicket::getId, ticketIds));
        }
        assetMapper.delete(new LambdaQueryWrapper<CustomerAsset>().in(CustomerAsset::getCustomerId, ids));
        addressMapper.delete(new LambdaQueryWrapper<CustomerAddress>().in(CustomerAddress::getCustomerId, ids));
        followMapper.delete(new LambdaQueryWrapper<CustomerFollow>().in(CustomerFollow::getCustomerId, ids));
        preferenceMapper.delete(new LambdaQueryWrapper<CustomerPreference>().in(CustomerPreference::getCustomerId, ids));
        analysisMapper.delete(new LambdaQueryWrapper<CustomerAnalysis>().in(CustomerAnalysis::getCustomerId, ids));
        List<RepayAgreement> agreements = agreementMapper.selectList(new LambdaQueryWrapper<RepayAgreement>().in(RepayAgreement::getCustomerId, ids));
        List<Long> agreementIds = agreements.stream().map(RepayAgreement::getId).toList();
        if (!agreementIds.isEmpty()) {
            remindLogMapper.delete(new LambdaQueryWrapper<RepayRemindLog>().in(RepayRemindLog::getAgreementId, agreementIds));
            agreementLogMapper.delete(new LambdaQueryWrapper<RepayAgreementLog>().in(RepayAgreementLog::getAgreementId, agreementIds));
            agreementMapper.delete(new LambdaQueryWrapper<RepayAgreement>().in(RepayAgreement::getId, agreementIds));
        }
        noticeMapper.delete(new LambdaQueryWrapper<AdminNotice>().eq(AdminNotice::getBizType, "CUSTOMER").in(AdminNotice::getBizId, ids));
        customerMapper.delete(new LambdaQueryWrapper<Customer>().in(Customer::getId, ids));
        return new CustomerVo.DemoResult(true, ids.size(), "已清除 " + ids.size() + " 位演示客户");
    }

    private void insertOne(String line, int index, LocalDate today, LocalDateTime now) {
        String[] col = line.split("\\|", -1);
        Customer customer = new Customer();
        customer.setTenantId(1L);
        customer.setName(col[0]);
        customer.setMobile("13855" + String.format("%06d", 1001 + index));
        customer.setCustomerType(col[1]);
        customer.setRegion(col[2]);
        customer.setShopName(col[3].isBlank() ? null : col[3]);
        customer.setMachineCount(Integer.parseInt(col[4]));
        customer.setMainProducts(col[5].isBlank() ? null : col[5]);
        customer.setTags(col[6].isBlank() ? null : col[6]);
        customer.setLevelCode(col[7]);
        customer.setLevel(rank(col[7]));
        customer.setLevelManual("1".equals(col[8]) ? 1 : 0);
        customer.setSource("DEMO");
        customer.setOpenid("demo-" + customer.getMobile());
        customer.setReferrer(index % 4 == 0 ? "老客户介绍" : null);
        customer.setRemark("演示客户，可随时清除");
        customer.setTotalPaid(0L);
        customer.setDeleted(0);
        customer.setWechat(index % 5 == 0 ? "wx_demo_" + index : null);
        int daysAgo = Integer.parseInt(col[16]);
        customer.setCreatedAt(daysAgo > 180 ? now.minusDays(daysAgo) : now.minusDays(30L + index));
        customer.setUpdatedAt(now);
        customerMapper.insert(customer);

        CustomerAddress address = new CustomerAddress();
        address.setCustomerId(customer.getId());
        address.setRegion(col[2]);
        address.setDetail(col[2] + "示范路 " + (10 + index) + " 号");
        address.setContactName(col[0]);
        address.setContactMobile(customer.getMobile());
        address.setDefaultFlag(1);
        address.setSource("MANUAL");
        address.setCreatedAt(now);
        addressMapper.insert(address);

        CustomerFollow follow = new CustomerFollow();
        follow.setCustomerId(customer.getId());
        follow.setFollowDate(today.minusDays(Math.max(daysAgo, 1)));
        follow.setContent(index % 2 == 0 ? "电话确认近期还要补一台平缝机。" : "上门看过车间，暂时没有新的采购计划。");
        follow.setNextDate(today.plusDays(7L + (index % 5)));
        follow.setOperatorName("老板");
        follow.setCreatedAt(now);
        followMapper.insert(follow);

        long payable = Long.parseLong(col[9]);
        long paid = Long.parseLong(col[10]);
        Long orderId = null;
        if (payable > 0) {
            orderId = insertOrder(customer, index, payable, paid, col[11], Long.parseLong(col[12]), col[13], daysAgo, now);
            insertAsset(customer, index, col[11], "INTERNAL", today.minusDays(Math.max(daysAgo, 1)), false);
        }
        if ("1".equals(col[14])) {
            insertRepair(customer, index, today, now);
        }
        String agreement = col[15];
        if (!"NONE".equals(agreement)) {
            insertAgreement(customer, orderId, agreement, payable - paid, today, now);
        }
    }

    private Long insertOrder(Customer customer, int index, long payable, long paid, String productName, long skuId, String delivery, int daysAgo, LocalDateTime now) {
        LocalDateTime created = now.minusDays(Math.max(daysAgo, 0));
        SalesOrder order = new SalesOrder();
        order.setTenantId(1L);
        order.setOrderNo("DM" + customer.getId());
        order.setCustomerId(customer.getId());
        order.setTotalAmount(payable);
        order.setDepositAmount(0L);
        order.setPaidAmount(paid);
        order.setBalanceAmount(Math.max(payable - paid, 0));
        order.setStatus(paid <= 0 ? "PENDING_PAYMENT" : paid < payable ? "PARTIALLY_PAID" : "PAID");
        order.setSource("DEMO");
        order.setGoodsAmount(payable);
        order.setInstallAmount(0L);
        order.setFreightAmount(0L);
        order.setAdjustAmount(0L);
        order.setPayableAmount(payable);
        order.setPriceAdjusted(0);
        order.setAddressText(customer.getRegion() + "示范路 " + (10 + index) + " 号");
        order.setCustomerName(customer.getName());
        order.setCustomerMobile(customer.getMobile());
        order.setDeleted(0);
        order.setCreatedAt(created);
        order.setUpdatedAt(created);
        orderMapper.insert(order);

        OrderLine line = new OrderLine();
        line.setOrderId(order.getId());
        line.setSkuId(skuId);
        line.setProductName(productName);
        line.setSkuName("标准");
        line.setQuantity(1);
        line.setUnitPrice(payable);
        line.setLineAmount(payable);
        line.setDeliveryMethod(delivery);
        if ("ON_SITE_INSTALL".equals(delivery)) {
            line.setInstallFee(0L);
        }
        orderLineMapper.insert(line);

        if (paid > 0 && paid < payable) {
            insertPayment(order, index, 1, paid, "SUCCESS", "定金", null);
            insertPayment(order, index, 2, payable - paid, "WAIT", "（余款）", null);
        } else if (paid >= payable && payable > 0) {
            insertPayment(order, index, 1, payable, "SUCCESS", "全款", index % 2 == 0 ? "WECHAT_TRANSFER" : "BANK_TRANSFER");
        } else if (payable > 0) {
            insertPayment(order, index, 1, payable, "WAIT", "全款", null);
        }
        return order.getId();
    }

    private void insertPayment(SalesOrder order, int index, int sort, long amount, String status, String title, String offline) {
        PaymentBill bill = new PaymentBill();
        bill.setTenantId(1L);
        bill.setOrderId(order.getId());
        String no = order.getOrderNo() + "-" + sort;
        bill.setOutTradeNo(no);
        bill.setType("BALANCE".equals(title) || "（余款）".equals(title) ? "BALANCE" : "FULL");
        bill.setAmount(amount);
        bill.setChannel("OFFLINE");
        bill.setStatus(status);
        bill.setBizType("ORDER");
        bill.setBizId(order.getId());
        bill.setPaymentNo(no);
        bill.setTitle(title);
        bill.setSortNo(sort);
        bill.setOfflineMethod("SUCCESS".equals(status) ? (offline == null ? "CASH" : offline) : null);
        bill.setPaidAt("SUCCESS".equals(status) ? order.getCreatedAt() : null);
        bill.setCreatedAt(order.getCreatedAt());
        bill.setUpdatedAt(order.getCreatedAt());
        paymentBillMapper.insert(bill);
    }

    private void insertRepair(Customer customer, int index, LocalDate today, LocalDateTime now) {
        CustomerAsset asset = insertAsset(customer, index + 100, "外购拷边机", "EXTERNAL", today.minusYears(3), true);
        RepairTicket ticket = new RepairTicket();
        ticket.setTenantId(1L);
        ticket.setTicketNo("RT" + customer.getId());
        ticket.setCustomerId(customer.getId());
        ticket.setCustomerName(customer.getName());
        ticket.setStatus("PENDING_PAYMENT");
        ticket.setContactMobile(customer.getMobile());
        ticket.setPayableAmount(15000L);
        ticket.setPaidAmount(0L);
        ticket.setQuoteAmount(15000L);
        ticket.setInWarranty(0);
        ticket.setCreatedAt(now.minusDays(2));
        ticket.setUpdatedAt(now.minusDays(2));
        repairTicketMapper.insert(ticket);
        RepairLine line = new RepairLine();
        line.setTicketId(ticket.getId());
        line.setLineNo(1);
        line.setAssetId(asset.getId());
        line.setBrand("外牌");
        line.setModel("外购拷边机");
        line.setSn(asset.getSn());
        line.setSource("EXTERNAL");
        line.setFaultType("NOISE");
        line.setFaultDesc("运转时有异响");
        line.setImagesJson("[]");
        line.setLineQuoteAmount(15000L);
        line.setInWarranty(0);
        repairLineMapper.insert(line);
        PaymentBill bill = new PaymentBill();
        bill.setTenantId(1L);
        bill.setOrderId(ticket.getId());
        String no = ticket.getTicketNo() + "-1";
        bill.setOutTradeNo(no);
        bill.setType("REPAIR");
        bill.setAmount(15000L);
        bill.setChannel("OFFLINE");
        bill.setStatus("WAIT");
        bill.setBizType("REPAIR");
        bill.setBizId(ticket.getId());
        bill.setPaymentNo(no);
        bill.setTitle("维修费");
        bill.setSortNo(1);
        bill.setCreatedAt(ticket.getCreatedAt());
        bill.setUpdatedAt(ticket.getCreatedAt());
        paymentBillMapper.insert(bill);
    }

    private CustomerAsset insertAsset(Customer customer, int index, String model, String source, LocalDate buyDate, boolean expired) {
        CustomerAsset asset = new CustomerAsset();
        asset.setTenantId(1L);
        asset.setCustomerId(customer.getId());
        asset.setModel(model);
        asset.setBrand("EXTERNAL".equals(source) ? "外牌" : "标准牌");
        asset.setSn(String.format("DEMO-SN-%04d", index + 1));
        asset.setBuyDate(buyDate);
        asset.setWarrantyEnd(expired ? buyDate.plusYears(1) : buyDate.plusYears(2));
        asset.setSource(source);
        asset.setCreatedAt(DateTimes.now());
        assetMapper.insert(asset);
        return asset;
    }

    private void insertAgreement(Customer customer, Long orderId, String kind, long unpaid, LocalDate today, LocalDateTime now) {
        RepayAgreement row = new RepayAgreement();
        row.setCustomerId(customer.getId());
        row.setScope(orderId == null ? "CUSTOMER" : "ORDER");
        row.setOrderId(orderId);
        row.setDueDate(switch (kind) {
            case "OVERDUE" -> today.minusDays(2);
            case "TODAY" -> today;
            case "SOON" -> today.plusDays(2);
            default -> today.minusDays(20);
        });
        long amount = unpaid > 0 ? unpaid : 100000L;
        row.setAgreedAmount(amount);
        row.setStatus("DONE".equals(kind) ? "FULFILLED" : "ACTIVE");
        row.setNote("DONE".equals(kind) ? "演示：已经履约" : "演示协议，可改期或作废");
        row.setOperatorName("老板");
        row.setCreatedAt(now.minusDays(5));
        row.setUpdatedAt(now.minusDays(1));
        agreementMapper.insert(row);
        RepayAgreementLog log = new RepayAgreementLog();
        log.setAgreementId(row.getId());
        log.setAction("CREATE");
        log.setSnapshotJson("{\"kind\":\"" + kind + "\"}");
        log.setOperatorName("老板");
        log.setCreatedAt(row.getCreatedAt());
        agreementLogMapper.insert(log);
    }

    private void requireEnabled() {
        if (!properties.getDemo().isEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "演示数据只在开发环境开放，生产环境不会生成");
        }
    }

    private static int rank(String code) {
        return switch (code) {
            case "SILVER" -> 2;
            case "GOLD" -> 3;
            case "VIP" -> 4;
            default -> 1;
        };
    }
}
