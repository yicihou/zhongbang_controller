package com.zhongbang.controller.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.mapper.order.OrderLineMapper;
import com.zhongbang.controller.mapper.order.PriceLogMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.order.ShipmentMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.technician.TechnicianMapper;
import com.zhongbang.controller.mapper.technician.TechnicianScheduleMapper;
import com.zhongbang.controller.mapper.workorder.WorkOrderMapper;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.pojo.order.dto.OrderRequests;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.PriceLog;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.order.entity.Shipment;
import com.zhongbang.controller.pojo.order.vo.OrderVo;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.technician.entity.Technician;
import com.zhongbang.controller.pojo.technician.entity.TechnicianSchedule;
import com.zhongbang.controller.pojo.workorder.entity.WorkOrder;
import com.zhongbang.controller.security.LoginUser;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.security.UserType;
import com.zhongbang.controller.service.cost.CostPriceService;
import com.zhongbang.controller.service.notice.NoticeService;
import com.zhongbang.controller.service.stock.StockAlertService;
import com.zhongbang.controller.service.stock.StockLogService;
import com.zhongbang.controller.service.technician.TechnicianRules;
import com.zhongbang.controller.support.idempotency.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订单的业务入口。小程序下单、我的订单，以及管理端的列表、发货、派工、取消、改价都从这里进。
 *
 * <p>金额和状态的判断在 {@link OrderRules}，这里负责查表、扣库存、占档期、把结果写回去。
 * 整个写操作标了 {@code @Transactional}：中途库存不够或状态不对，已经扣的库存会一起撤销。
 */
@Service
public class OrderService {

    private static final Set<String> METHODS = Set.of(
            "EXPRESS", "FREIGHT", "HOME_DELIVERY", "ON_SITE_INSTALL", "STORE_PICKUP");
    private static final Duration IDEM_TTL = Duration.ofHours(24);

    private final SalesOrderMapper salesOrderMapper;
    private final OrderLineMapper orderLineMapper;
    private final ShipmentMapper shipmentMapper;
    private final PriceLogMapper priceLogMapper;
    private final PaymentBillMapper paymentBillMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final CustomerMapper customerMapper;
    private final TechnicianMapper technicianMapper;
    private final TechnicianScheduleMapper scheduleMapper;
    private final WorkOrderMapper workOrderMapper;
    private final NoticeService noticeService;
    private final IdempotencyService idempotencyService;
    private final IdentifierGenerator idGenerator;
    private final CostPriceService costPriceService;
    private final StockAlertService stockAlertService;
    private final StockLogService stockLogService;

    public OrderService(
            SalesOrderMapper salesOrderMapper,
            OrderLineMapper orderLineMapper,
            ShipmentMapper shipmentMapper,
            PriceLogMapper priceLogMapper,
            PaymentBillMapper paymentBillMapper,
            ProductMapper productMapper,
            ProductSkuMapper productSkuMapper,
            CustomerMapper customerMapper,
            TechnicianMapper technicianMapper,
            TechnicianScheduleMapper scheduleMapper,
            WorkOrderMapper workOrderMapper,
            NoticeService noticeService,
            IdempotencyService idempotencyService,
            IdentifierGenerator idGenerator,
            CostPriceService costPriceService,
            StockAlertService stockAlertService,
            StockLogService stockLogService
    ) {
        this.salesOrderMapper = salesOrderMapper;
        this.orderLineMapper = orderLineMapper;
        this.shipmentMapper = shipmentMapper;
        this.priceLogMapper = priceLogMapper;
        this.paymentBillMapper = paymentBillMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.customerMapper = customerMapper;
        this.technicianMapper = technicianMapper;
        this.scheduleMapper = scheduleMapper;
        this.workOrderMapper = workOrderMapper;
        this.noticeService = noticeService;
        this.idempotencyService = idempotencyService;
        this.idGenerator = idGenerator;
        this.costPriceService = costPriceService;
        this.stockAlertService = stockAlertService;
        this.stockLogService = stockLogService;
    }

    /**
     * 客户下单。items 里可以有多行；不传 items 时用 skuId 当成一行，兼容旧的确认页。
     * 上门安装的安装费先空着，页面上是「待定」。默认生成一笔全款待付，运费为 0。
     * 同一个 requestId 再提交一次，直接返回已经生成的那张订单。
     */
    @Transactional
    public OrderVo.Created create(OrderRequests.Create request) {
        LoginUser user = requireCustomer();
        String requestId = request.getRequestId() == null ? "" : request.getRequestId().trim();
        if (!requestId.isEmpty()) {
            SalesOrder existing = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                    .eq(SalesOrder::getCustomerId, user.getUserId())
                    .eq(SalesOrder::getRequestId, requestId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return OrderAssembler.created(load(existing.getId()));
            }
        }
        String idemKey = requestId.isEmpty() ? null : "order:" + user.getUserId() + ":" + requestId;
        if (idemKey != null) {
            var hit = idempotencyService.begin(idemKey, IDEM_TTL);
            if (hit.isPresent() && hit.get().startsWith("ORDER:")) {
                return OrderAssembler.created(load(Long.valueOf(hit.get().substring(6))));
            }
            if (hit.isPresent()) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "订单正在提交，请稍后再试");
            }
        }
        try {
            OrderVo.Created created = insertOrder(user, request, requestId);
            if (idemKey != null) {
                idempotencyService.complete(idemKey, "ORDER:" + created.orderId(), IDEM_TTL);
            }
            return created;
        } catch (RuntimeException ex) {
            if (idemKey != null) {
                idempotencyService.forget(idemKey);
            }
            throw ex;
        }
    }

    public PageResult<OrderVo.Mine> myOrders(String status, int page, int size) {
        LoginUser user = requireCustomer();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<SalesOrder> wrapper = new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerId, user.getUserId())
                .orderByDesc(SalesOrder::getCreatedAt);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(SalesOrder::getStatus, status.trim());
        }
        Page<SalesOrder> result = salesOrderMapper.selectPage(new Page<>(p, s), wrapper);
        List<OrderVo.Mine> list = hydrate(result.getRecords()).stream().map(OrderAssembler::mine).toList();
        return PageResult.of(list, result.getTotal(), p, s);
    }

    public OrderVo.Mine myOrder(long id) {
        LoginUser user = requireCustomer();
        OrderBundle bundle = load(id);
        if (!user.getUserId().equals(bundle.getOrder().getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return OrderAssembler.mine(bundle);
    }

    @Transactional
    public OrderVo.Mine cancelMine(long id, OrderRequests.Cancel request) {
        LoginUser user = requireCustomer();
        OrderBundle bundle = load(id);
        if (!user.getUserId().equals(bundle.getOrder().getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return cancelAndRestore(bundle, request == null ? null : request.getReason());
    }

    public PageResult<OrderVo.Admin> adminList(String status, String keyword, int page, int size) {
        requireStaff();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<SalesOrder> wrapper = new LambdaQueryWrapper<SalesOrder>()
                .orderByDesc(SalesOrder::getCreatedAt);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SalesOrder::getStatus, status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(w -> w.like(SalesOrder::getOrderNo, like)
                    .or().like(SalesOrder::getCustomerName, like)
                    .or().like(SalesOrder::getCustomerMobile, like));
        }
        Page<SalesOrder> result = salesOrderMapper.selectPage(new Page<>(p, s), wrapper);
        List<OrderVo.Admin> list = hydrate(result.getRecords()).stream().map(OrderAssembler::admin).toList();
        return PageResult.of(list, result.getTotal(), p, s);
    }

    public OrderVo.Admin adminGet(long id) {
        requireStaff();
        return OrderAssembler.admin(load(id));
    }

    @Transactional
    public OrderVo.Admin adjust(long id, OrderRequests.AdjustPrice request) {
        LoginUser staff = requireStaff();
        OrderBundle bundle = load(id);
        OrderRules.adjustPrice(bundle, request, staff.getDisplayName(), this::nextId);
        save(bundle);
        return OrderAssembler.admin(bundle);
    }

    @Transactional
    public OrderVo.Admin freight(long id, OrderRequests.Freight request) {
        LoginUser staff = requireStaff();
        if (request == null || request.getFreightAmount() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写运费");
        }
        OrderBundle bundle = load(id);
        OrderRules.setFreight(bundle, request.getFreightAmount(), staff.getDisplayName(), this::nextId);
        save(bundle);
        return OrderAssembler.admin(bundle);
    }

    @Transactional
    public OrderVo.Admin createPayments(long id, OrderRequests.CreatePayments request) {
        requireStaff();
        OrderBundle bundle = load(id);
        OrderRules.createPayments(bundle, request, this::nextId);
        save(bundle);
        return OrderAssembler.admin(bundle);
    }

    @Transactional
    public OrderVo.Payment patchPayment(long paymentId, OrderRequests.PatchPayment request) {
        requireStaff();
        PaymentBill bill = paymentBillMapper.selectById(paymentId);
        if (bill == null || bill.getBizId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应付单不存在");
        }
        OrderBundle bundle = load(bill.getBizId());
        PaymentBill updated = OrderRules.patchPayment(bundle, paymentId, request == null ? new OrderRequests.PatchPayment() : request);
        save(bundle);
        return OrderAssembler.admin(bundle).payments().stream()
                .filter(item -> paymentId == item.id())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应付单不存在"));
    }

    @Transactional
    public OrderVo.Payment offlineConfirm(long paymentId, OrderRequests.OfflineConfirm request) {
        LoginUser staff = requireStaff();
        PaymentBill bill = paymentBillMapper.selectById(paymentId);
        if (bill == null || bill.getBizId() == null || !"ORDER".equals(bill.getBizType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应付单不存在");
        }
        OrderBundle bundle = load(bill.getBizId());
        OrderRules.confirmOffline(bundle, paymentId, request == null ? new OrderRequests.OfflineConfirm() : request,
                staff.getUserId(), staff.getDisplayName(), this::nextId);
        save(bundle);
        return OrderAssembler.admin(bundle).payments().stream()
                .filter(item -> paymentId == item.id())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应付单不存在"));
    }

    /** 按行发货。未付款也可以发。快递必须带承运商和单号。 */
    @Transactional
    public OrderVo.Admin ship(long id, OrderRequests.ShipmentCreate request) {
        requireStaff();
        if (request == null || request.getOrderItemId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要发货的商品行");
        }
        OrderBundle bundle = load(id);
        OrderRules.addShipment(bundle, request, this::nextId);
        costPriceService.fillOnShip(bundle);
        save(bundle);
        return OrderAssembler.admin(bundle);
    }

    /** 给上门安装行派师傅。不要求已经付款。档期已满时报 3002。 */
    @Transactional
    public OrderVo.Admin assign(long id, OrderRequests.Assign request) {
        requireStaff();
        if (request == null || request.getTechnicianId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "师傅不存在");
        }
        if (request.getInstallDate() == null || request.getInstallDate().isBlank()
                || request.getInstallSlot() == null || request.getInstallSlot().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写安装日期和时段");
        }
        Technician technician = TechnicianRules.requireAssignable(technicianMapper.selectById(request.getTechnicianId()));
        OrderBundle bundle = load(id);
        OrderRules.assertFulfillable(bundle.getOrder(), "派工");
        OrderLine item = bundle.getItems().stream()
                .filter(line -> request.getOrderItemId() != null && request.getOrderItemId().equals(line.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单行不存在"));
        if (!"ON_SITE_INSTALL".equals(item.getDeliveryMethod())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅上门安装行可派师傅");
        }
        String date = request.getInstallDate().trim();
        String slot = normalizeSlot(request.getInstallSlot());
        boolean sameSlot = technician.getId().equals(item.getTechnicianId())
                && date.equals(item.getInstallDate())
                && slot.equals(item.getInstallSlot());
        if (!sameSlot) {
            releaseSchedule(item.getTechnicianId(), item.getInstallDate(), item.getInstallSlot());
            occupySchedule(technician.getId(), date, slot, bundle.getOrder().getOrderNo(), bundle.getOrder().getCustomerName());
        }
        item.setTechnicianId(technician.getId());
        item.setTechnicianName(technician.getName());
        item.setInstallDate(date);
        item.setInstallSlot(slot);
        if (item.getWorkOrderNo() == null || item.getWorkOrderNo().isBlank()) {
            String digits = date.replace("-", "");
            String idText = String.valueOf(item.getId());
            item.setWorkOrderNo("WO" + digits + idText.substring(Math.max(0, idText.length() - 4)));
            insertWorkOrder(bundle.getOrder(), item, technician, date, slot);
        }
        String appointAt = date + " " + slot;
        if (!OrderRules.hasActiveShipment(item)) {
            OrderRequests.ShipmentCreate ship = new OrderRequests.ShipmentCreate();
            ship.setOrderItemId(item.getId());
            ship.setCourierName(technician.getName());
            ship.setCourierMobile(technician.getMobile());
            ship.setAppointAt(appointAt);
            ship.setRemark(request.getReason());
            OrderRules.addShipment(bundle, ship, this::nextId);
        } else if (item.getShipment() != null) {
            item.getShipment().setCourierName(technician.getName());
            item.getShipment().setCourierMobile(technician.getMobile());
            item.getShipment().setAppointAt(appointAt);
            item.getShipment().setUpdatedAt(DateTimes.now());
        }
        if (request.getReason() != null && !request.getReason().isBlank()) {
            String extra = "派工：" + request.getReason().trim();
            String origin = bundle.getOrder().getAdminRemark();
            bundle.getOrder().setAdminRemark(origin == null || origin.isBlank() ? extra : origin + "\n" + extra);
        }
        OrderRules.syncStatus(bundle);
        save(bundle);
        return OrderAssembler.admin(bundle);
    }

    @Transactional
    public OrderVo.Admin cancel(long id, OrderRequests.Cancel request) {
        requireStaff();
        OrderBundle bundle = load(id);
        cancelAndRestore(bundle, request == null ? null : request.getReason());
        return OrderAssembler.admin(bundle);
    }

    private OrderVo.Created insertOrder(LoginUser user, OrderRequests.Create request, String requestId) {
        List<OrderRequests.Line> lines = normalizeLines(request);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择商品");
        }
        Customer customer = customerMapper.selectById(user.getUserId());
        String name = firstNonBlank(request.getContactName(), customer == null ? null : customer.getName(), user.getDisplayName(), "客户");
        String mobile = firstNonBlank(request.getContactMobile(), customer == null ? null : customer.getMobile(), "");
        String address = request.getAddressText() == null ? "" : request.getAddressText().trim();
        if (address.isEmpty() && request.getAddressId() != null) {
            address = "地址#" + request.getAddressId();
        }
        if (address.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写收货地址");
        }
        LocalDateTime now = DateTimes.now();
        SalesOrder order = new SalesOrder();
        order.setId(nextId());
        order.setTenantId(1L);
        order.setOrderNo(nextOrderNo());
        order.setCustomerId(user.getUserId());
        order.setStatus("PENDING_PAYMENT");
        order.setSource("MINIAPP");
        order.setDeleted(0);
        order.setDepositAmount(0L);
        order.setFreightAmount(0L);
        order.setAdjustAmount(0L);
        order.setPriceAdjusted(0);
        order.setCustomerName(name);
        order.setCustomerMobile(mobile);
        order.setAddressText(address);
        order.setContactRemark(blankToNull(request.getRemark()));
        order.setRemark(order.getContactRemark());
        order.setRequestId(requestId.isEmpty() ? null : requestId);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        OrderBundle bundle = new OrderBundle();
        bundle.setOrder(order);
        List<OrderLine> items = new ArrayList<>();
        for (OrderRequests.Line line : lines) {
            items.add(buildLine(order, line, address));
        }
        bundle.setItems(items);
        OrderRules.recalc(bundle);
        if (nz(order.getPayableAmount()) > 0) {
            bundle.addPayment(OrderRules.buildPayment(bundle, this::nextId, "FULL", "全款", order.getPayableAmount(), 1, DateTimes.format(now)));
        }
        OrderRules.syncStatus(bundle);
        salesOrderMapper.insert(order);
        for (OrderLine item : items) {
            orderLineMapper.insert(item);
        }
        for (PaymentBill bill : bundle.getFreshPayments()) {
            paymentBillMapper.insert(bill);
        }
        noticeService.orderCreated(order);
        return OrderAssembler.created(bundle);
    }

    private OrderLine buildLine(SalesOrder order, OrderRequests.Line line, String address) {
        if (line.getSkuId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择商品规格");
        }
        int quantity = line.getQuantity() == null ? 1 : line.getQuantity();
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数量须大于 0");
        }
        ProductSku sku = productSkuMapper.selectById(line.getSkuId());
        if (sku == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品规格不存在");
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || !"ON".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品已下架");
        }
        long price = sku.getPrice() == null ? 0L : sku.getPrice();
        if (line.getExpectedPrice() != null && line.getExpectedPrice() != price) {
            throw new BusinessException(ErrorCode.AMOUNT_MISMATCH, "商品价格已变化，请确认后再提交");
        }
        String method = normalizeMethod(line.getDeliveryMethod(), line.getInstallDate(), line.getTechnicianId());
        if (productSkuMapper.deductStock(sku.getId(), quantity) != 1) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT, "库存不足");
        }
        // 卖出去也算一笔库存变动，流水里记负数。少了这一笔，老板按进货流水对库存会对不上。
        stockLogService.record(sku.getId(), StockLogService.SALE_OUT, order.getId(), -quantity, sku.getCostPrice());
        stockAlertService.evaluate(sku.getId());
        OrderLine item = new OrderLine();
        item.setId(nextId());
        item.setOrderId(order.getId());
        item.setSkuId(sku.getId());
        item.setProductName(product.getName());
        item.setSkuName(sku.getTitle());
        item.setCoverImage(product.getCoverImage());
        item.setQuantity(quantity);
        item.setUnitPrice(price);
        item.setLineAmount(item.getUnitPrice() * quantity);
        item.setDeliveryMethod(method);
        if ("ON_SITE_INSTALL".equals(method)) {
            item.setInstallFee(null);
            item.setInstallDate(blankToNull(line.getInstallDate()));
            item.setInstallSlot(line.getInstallSlot() == null ? null : normalizeSlot(line.getInstallSlot()));
            if (line.getTechnicianId() != null) {
                Technician technician = TechnicianRules.requireAssignable(technicianMapper.selectById(line.getTechnicianId()));
                item.setTechnicianId(technician.getId());
                item.setTechnicianName(technician.getName());
            }
            item.setAddressSnapshotJson("{\"detail\":\"" + address.replace("\"", "") + "\"}");
        } else {
            item.setInstallFee(0L);
        }
        costPriceService.snapshot(item, sku);
        return item;
    }

    private List<OrderRequests.Line> normalizeLines(OrderRequests.Create request) {
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            return request.getItems();
        }
        if (request.getSkuId() == null) {
            return List.of();
        }
        OrderRequests.Line line = new OrderRequests.Line();
        line.setSkuId(request.getSkuId());
        line.setQuantity(request.getQuantity());
        line.setDeliveryMethod(request.getDeliveryMethod());
        line.setInstallDate(request.getInstallDate());
        line.setInstallSlot(request.getInstallSlot());
        line.setTechnicianId(request.getTechnicianId());
        return List.of(line);
    }

    private OrderVo.Mine cancelAndRestore(OrderBundle bundle, String reason) {
        OrderRules.cancel(bundle, reason);
        for (OrderLine line : bundle.getItems()) {
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            productSkuMapper.restoreStock(line.getSkuId(), quantity);
            // 取消把货退回来，流水记正数，和下单时的 SALE_OUT 对冲。
            stockLogService.record(line.getSkuId(), StockLogService.SALE_CANCEL, bundle.getOrder().getId(), quantity, null);
            stockAlertService.evaluate(line.getSkuId());
        }
        save(bundle);
        return OrderAssembler.mine(bundle);
    }

    private void occupySchedule(long technicianId, String date, String slot, String orderNo, String customerName) {
        TechnicianSchedule row = scheduleMapper.selectOne(new LambdaQueryWrapper<TechnicianSchedule>()
                .eq(TechnicianSchedule::getTechnicianId, technicianId)
                .eq(TechnicianSchedule::getServiceDate, date)
                .eq(TechnicianSchedule::getTimeSlot, slot)
                .last("LIMIT 1"));
        if (row == null) {
            row = new TechnicianSchedule();
            row.setId(nextId());
            row.setTechnicianId(technicianId);
            row.setServiceDate(date);
            row.setTimeSlot(slot);
            row.setCapacity(2);
            row.setUsedCount(0);
            row.setOrderNo(orderNo);
            row.setCustomerName(customerName);
            scheduleMapper.insert(row);
        }
        int used = row.getUsedCount() == null ? 0 : row.getUsedCount();
        int capacity = row.getCapacity() == null ? 2 : row.getCapacity();
        if (used >= capacity) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "师傅档期冲突");
        }
        row.setUsedCount(used + 1);
        if (row.getOrderNo() == null) {
            row.setOrderNo(orderNo);
        }
        if (row.getCustomerName() == null) {
            row.setCustomerName(customerName);
        }
        scheduleMapper.updateById(row);
    }

    private void releaseSchedule(Long technicianId, String date, String slot) {
        if (technicianId == null || date == null || date.isBlank() || slot == null || slot.isBlank()) {
            return;
        }
        TechnicianSchedule row = scheduleMapper.selectOne(new LambdaQueryWrapper<TechnicianSchedule>()
                .eq(TechnicianSchedule::getTechnicianId, technicianId)
                .eq(TechnicianSchedule::getServiceDate, date)
                .eq(TechnicianSchedule::getTimeSlot, slot)
                .last("LIMIT 1"));
        if (row != null && row.getUsedCount() != null && row.getUsedCount() > 0) {
            row.setUsedCount(row.getUsedCount() - 1);
            scheduleMapper.updateById(row);
        }
    }

    private void insertWorkOrder(SalesOrder order, OrderLine item, Technician technician, String date, String slot) {
        WorkOrder work = new WorkOrder();
        work.setId(nextId());
        work.setTenantId(1L);
        work.setWorkNo(item.getWorkOrderNo());
        work.setType("INSTALL");
        work.setOrderId(order.getId());
        work.setOrderItemId(item.getId());
        work.setCustomerId(order.getCustomerId());
        work.setTechnicianId(technician.getId());
        work.setStatus("ASSIGNED");
        work.setQuoteAmount(0L);
        work.setActualAmount(0L);
        work.setAppointmentAt(appointment(date, slot));
        work.setCreatedAt(DateTimes.now());
        work.setUpdatedAt(DateTimes.now());
        workOrderMapper.insert(work);
    }

    private LocalDateTime appointment(String date, String slot) {
        LocalTime time = switch (slot) {
            case "下午" -> LocalTime.of(14, 0);
            case "晚上" -> LocalTime.of(18, 0);
            default -> LocalTime.of(9, 0);
        };
        return LocalDate.parse(date).atTime(time);
    }

    private void save(OrderBundle bundle) {
        SalesOrder order = bundle.getOrder();
        order.setUpdatedAt(DateTimes.now());
        order.setRemark(order.getContactRemark());
        salesOrderMapper.updateById(order);
        for (Shipment shipment : bundle.getFreshShipments()) {
            shipmentMapper.insert(shipment);
        }
        for (OrderLine line : bundle.getItems()) {
            orderLineMapper.updateById(line);
            if (line.getShipment() != null && !bundle.getFreshShipments().contains(line.getShipment())) {
                line.getShipment().setUpdatedAt(DateTimes.now());
                shipmentMapper.updateById(line.getShipment());
            }
        }
        for (PaymentBill bill : bundle.getPayments()) {
            if (bundle.getFreshPayments().contains(bill)) {
                paymentBillMapper.insert(bill);
            } else {
                paymentBillMapper.updateById(bill);
            }
        }
        for (PriceLog log : bundle.getFreshLogs()) {
            priceLogMapper.insert(log);
        }
    }

    public OrderBundle load(long id) {
        SalesOrder order = salesOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        List<OrderBundle> bundles = hydrate(List.of(order));
        return bundles.get(0);
    }

    private List<OrderBundle> hydrate(List<SalesOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(SalesOrder::getId).toList();
        List<OrderLine> lines = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLine>().in(OrderLine::getOrderId, ids));
        List<PaymentBill> payments = paymentBillMapper.selectList(new LambdaQueryWrapper<PaymentBill>()
                .eq(PaymentBill::getBizType, "ORDER")
                .in(PaymentBill::getBizId, ids));
        List<Shipment> shipments = shipmentMapper.selectList(new LambdaQueryWrapper<Shipment>().in(Shipment::getOrderId, ids));
        List<PriceLog> logs = priceLogMapper.selectList(new LambdaQueryWrapper<PriceLog>().in(PriceLog::getOrderId, ids));
        Map<Long, Shipment> shipmentById = new HashMap<>();
        for (Shipment shipment : shipments) {
            shipmentById.put(shipment.getId(), shipment);
        }
        for (OrderLine line : lines) {
            if (line.getShipmentId() != null) {
                line.setShipment(shipmentById.get(line.getShipmentId()));
            }
        }
        List<OrderBundle> bundles = new ArrayList<>();
        for (SalesOrder order : orders) {
            OrderBundle bundle = new OrderBundle();
            bundle.setOrder(order);
            bundle.setItems(new ArrayList<>(lines.stream().filter(line -> order.getId().equals(line.getOrderId())).toList()));
            bundle.setPayments(new ArrayList<>(payments.stream()
                    .filter(bill -> order.getId().equals(bill.getBizId()))
                    .sorted(Comparator.comparingInt(bill -> bill.getSortNo() == null ? 0 : bill.getSortNo()))
                    .toList()));
            bundle.setPriceLogs(new ArrayList<>(logs.stream()
                    .filter(log -> order.getId().equals(log.getOrderId()))
                    .sorted(Comparator.comparing(PriceLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList()));
            bundles.add(bundle);
        }
        return bundles;
    }

    private String nextOrderNo() {
        String day = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "SO" + day;
        Long count = salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>().likeRight(SalesOrder::getOrderNo, prefix));
        return prefix + String.format("%04d", (count == null ? 0 : count) + 1);
    }

    private long nextId() {
        return idGenerator.nextId(null).longValue();
    }

    private String normalizeMethod(String method, String installDate, Long technicianId) {
        if (method == null || method.isBlank()) {
            if ((installDate != null && !installDate.isBlank()) || technicianId != null) {
                return "ON_SITE_INSTALL";
            }
            return "EXPRESS";
        }
        String value = method.trim().toUpperCase();
        if (!METHODS.contains(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "发货方式不正确");
        }
        return value;
    }

    private String normalizeSlot(String raw) {
        String text = raw.trim();
        return switch (text.toUpperCase()) {
            case "AM" -> "上午";
            case "PM" -> "下午";
            case "EVE", "NIGHT" -> "晚上";
            default -> text;
        };
    }

    private LoginUser requireCustomer() {
        LoginUser user = UserContext.require();
        if (user.getUserType() != UserType.CUSTOMER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请使用客户账号");
        }
        return user;
    }

    private LoginUser requireStaff() {
        LoginUser user = UserContext.require();
        if (user.getUserType() != UserType.STAFF) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
