package com.zhongbang.controller.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.enums.BizType;
import com.zhongbang.controller.common.enums.DeliveryMethod;
import com.zhongbang.controller.common.enums.OrderStatus;
import com.zhongbang.controller.common.enums.PaymentChannel;
import com.zhongbang.controller.common.enums.PaymentStatus;
import com.zhongbang.controller.common.enums.PaymentType;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.customer.entity.CustomerAddress;
import com.zhongbang.controller.module.customer.mapper.CustomerAddressMapper;
import com.zhongbang.controller.module.notify.AdminNotifyService;
import com.zhongbang.controller.module.order.OrderStateMachine;
import com.zhongbang.controller.module.order.dto.CreateOrderRequest;
import com.zhongbang.controller.module.order.dto.OrderResponse;
import com.zhongbang.controller.module.order.entity.CartItem;
import com.zhongbang.controller.module.order.entity.OrderItem;
import com.zhongbang.controller.module.order.entity.SalesOrder;
import com.zhongbang.controller.module.order.mapper.CartItemMapper;
import com.zhongbang.controller.module.order.mapper.OrderItemMapper;
import com.zhongbang.controller.module.order.mapper.SalesOrderMapper;
import com.zhongbang.controller.module.payment.entity.Payment;
import com.zhongbang.controller.module.payment.mapper.PaymentMapper;
import com.zhongbang.controller.module.product.entity.Product;
import com.zhongbang.controller.module.product.entity.ProductSku;
import com.zhongbang.controller.module.product.mapper.ProductMapper;
import com.zhongbang.controller.module.product.mapper.ProductSkuMapper;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.support.idempotency.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);

    private final SalesOrderMapper salesOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final PaymentMapper paymentMapper;
    private final CustomerAddressMapper customerAddressMapper;
    private final AdminNotifyService adminNotifyService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public OrderService(
            SalesOrderMapper salesOrderMapper,
            OrderItemMapper orderItemMapper,
            CartItemMapper cartItemMapper,
            ProductSkuMapper productSkuMapper,
            ProductMapper productMapper,
            PaymentMapper paymentMapper,
            CustomerAddressMapper customerAddressMapper,
            AdminNotifyService adminNotifyService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper
    ) {
        this.salesOrderMapper = salesOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.cartItemMapper = cartItemMapper;
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
        this.paymentMapper = paymentMapper;
        this.customerAddressMapper = customerAddressMapper;
        this.adminNotifyService = adminNotifyService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Long customerId = UserContext.require().getUserId();
        var prior = idempotencyService.begin(request.requestId(), IDEMPOTENCY_TTL);
        if (prior.isPresent()) {
            Long orderId = Long.valueOf(prior.get());
            return detailForCustomer(orderId);
        }

        String addressJson = null;
        if (request.addressId() != null) {
            CustomerAddress address = customerAddressMapper.selectById(request.addressId());
            if (address == null || !customerId.equals(address.getCustomerId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "收货地址不存在");
            }
            addressJson = toAddressJson(address);
        }

        LocalDateTime now = LocalDateTime.now();
        long goodsAmount = 0L;
        long installAmount = 0L;
        List<OrderItem> lines = new ArrayList<>();

        for (CreateOrderRequest.Item lineReq : request.items()) {
            ProductSku sku = productSkuMapper.selectById(lineReq.skuId());
            if (sku == null || (sku.getDeleted() != null && sku.getDeleted() == 1)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在: " + lineReq.skuId());
            }
            int available = (sku.getStock() == null ? 0 : sku.getStock()) - (sku.getLocked() == null ? 0 : sku.getLocked());
            if (available < lineReq.quantity()) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
            }
            Product product = productMapper.selectById(sku.getProductId());
            DeliveryMethod method = lineReq.deliveryMethod();
            long lineAmount = sku.getPrice() * lineReq.quantity();
            goodsAmount += lineAmount;

            OrderItem item = new OrderItem();
            item.setSkuId(sku.getId());
            item.setProductName(product == null ? "" : product.getName());
            item.setSkuName(sku.getTitle());
            item.setQuantity(lineReq.quantity());
            item.setUnitPrice(sku.getPrice());
            item.setLineAmount(lineAmount);
            item.setDeliveryMethod(method);
            item.setInstallFee(0L);
            item.setInstallDate(lineReq.installDate());
            item.setInstallSlot(lineReq.installSlot());
            item.setTechnicianId(lineReq.technicianId());
            item.setAddressSnapshotJson(addressJson);
            item.setCreatedAt(now);
            lines.add(item);

            sku.setLocked((sku.getLocked() == null ? 0 : sku.getLocked()) + lineReq.quantity());
            productSkuMapper.updateById(sku);
        }

        long payable = goodsAmount + installAmount; // freight=0, adjust=0 at checkout (D11)
        SalesOrder order = new SalesOrder();
        order.setTenantId(1L);
        order.setOrderNo(nextOrderNo());
        order.setCustomerId(customerId);
        order.setTotalAmount(payable);
        order.setDepositAmount(0L);
        order.setPaidAmount(0L);
        order.setBalanceAmount(payable);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setSource("MINIAPP");
        order.setGoodsAmount(goodsAmount);
        order.setInstallAmount(installAmount);
        order.setFreightAmount(0L);
        order.setAdjustAmount(0L);
        order.setPayableAmount(payable);
        order.setPriceAdjusted(0);
        order.setAddressId(request.addressId());
        order.setAddressJson(addressJson);
        order.setRemark(request.remark());
        order.setContactRemark(request.remark());
        order.setDeleted(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        salesOrderMapper.insert(order);

        for (OrderItem item : lines) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        Payment payment = new Payment();
        payment.setTenantId(1L);
        payment.setOrderId(order.getId());
        payment.setBizType(BizType.ORDER);
        payment.setBizId(order.getId());
        payment.setPaymentNo(nextPaymentNo());
        payment.setOutTradeNo(payment.getPaymentNo());
        payment.setPaymentType(PaymentType.FULL);
        payment.setTitle("全款");
        payment.setAmount(payable);
        payment.setChannel(PaymentChannel.OFFLINE);
        payment.setStatus(PaymentStatus.WAIT);
        payment.setSortNo(1);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentMapper.insert(payment);

        // Clear matching cart rows for purchased SKUs
        for (CreateOrderRequest.Item lineReq : request.items()) {
            cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                    .eq(CartItem::getCustomerId, customerId)
                    .eq(CartItem::getSkuId, lineReq.skuId())
                    .eq(CartItem::getDeliveryMethod, lineReq.deliveryMethod()));
        }

        adminNotifyService.publish(
                "ORDER_CREATED",
                "新订单 " + order.getOrderNo(),
                "应付 ¥" + formatYuan(payable) + "，待线下确认",
                "ORDER",
                order.getId()
        );

        idempotencyService.complete(request.requestId(), String.valueOf(order.getId()), IDEMPOTENCY_TTL);
        return toResponse(order, lines, List.of(payment));
    }

    public PageResult<OrderResponse> listMine(int page, int size) {
        Long customerId = UserContext.require().getUserId();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        Page<SalesOrder> mp = salesOrderMapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<SalesOrder>()
                        .eq(SalesOrder::getCustomerId, customerId)
                        .orderByDesc(SalesOrder::getCreatedAt));
        List<OrderResponse> list = mp.getRecords().stream()
                .map(o -> toResponse(o, loadItems(o.getId()), loadPayments(o.getId())))
                .collect(Collectors.toList());
        return PageResult.of(list, mp.getTotal(), p, s);
    }

    public OrderResponse detailMine(Long id) {
        return detailForCustomer(id);
    }

    @Transactional
    public OrderResponse cancelMine(Long id) {
        Long customerId = UserContext.require().getUserId();
        SalesOrder order = salesOrderMapper.selectById(id);
        if (order == null || !customerId.equals(order.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        OrderStateMachine.assertTransition(order.getStatus(), OrderStatus.CANCELLED);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅待支付订单可取消");
        }
        List<Payment> payments = loadPayments(id);
        boolean anyPaid = payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.SUCCESS);
        if (anyPaid) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已有成功付款，无法取消");
        }
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.WAIT) {
                payment.setStatus(PaymentStatus.CLOSED);
                payment.setUpdatedAt(LocalDateTime.now());
                paymentMapper.updateById(payment);
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        salesOrderMapper.updateById(order);
        return toResponse(order, loadItems(id), loadPayments(id));
    }

    public PageResult<OrderResponse> adminList(OrderStatus status, int page, int size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<SalesOrder> q = new LambdaQueryWrapper<SalesOrder>()
                .orderByDesc(SalesOrder::getCreatedAt);
        if (status != null) {
            q.eq(SalesOrder::getStatus, status);
        }
        Page<SalesOrder> mp = salesOrderMapper.selectPage(new Page<>(p, s), q);
        List<OrderResponse> list = mp.getRecords().stream()
                .map(o -> toResponse(o, loadItems(o.getId()), loadPayments(o.getId())))
                .collect(Collectors.toList());
        return PageResult.of(list, mp.getTotal(), p, s);
    }

    public OrderResponse adminDetail(Long id) {
        SalesOrder order = salesOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return toResponse(order, loadItems(id), loadPayments(id));
    }

    private OrderResponse detailForCustomer(Long id) {
        Long customerId = UserContext.require().getUserId();
        SalesOrder order = salesOrderMapper.selectById(id);
        if (order == null || !customerId.equals(order.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return toResponse(order, loadItems(id), loadPayments(id));
    }

    private List<OrderItem> loadItems(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
    }

    private List<Payment> loadPayments(Long orderId) {
        return paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getBizType, BizType.ORDER)
                .eq(Payment::getBizId, orderId)
                .orderByAsc(Payment::getSortNo)
                .orderByAsc(Payment::getId));
    }

    private OrderResponse toResponse(SalesOrder order, List<OrderItem> items, List<Payment> payments) {
        List<OrderResponse.Item> itemDtos = items.stream()
                .map(i -> new OrderResponse.Item(
                        i.getId(), i.getSkuId(), i.getProductName(), i.getSkuName(),
                        i.getQuantity(), i.getUnitPrice(), i.getLineAmount(),
                        i.getDeliveryMethod(), i.getInstallFee(),
                        i.getInstallDate(), i.getInstallSlot(), i.getTechnicianId()
                ))
                .collect(Collectors.toList());
        List<OrderResponse.PaymentBrief> payDtos = payments.stream()
                .map(p -> new OrderResponse.PaymentBrief(
                        p.getId(), p.getPaymentNo(), p.getPaymentType(),
                        p.getTitle(), p.getAmount(), p.getStatus()
                ))
                .collect(Collectors.toList());
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getGoodsAmount(),
                order.getInstallAmount(),
                order.getFreightAmount(),
                order.getAdjustAmount(),
                order.getPayableAmount(),
                order.getPaidAmount(),
                order.getRemark(),
                order.getCreatedAt(),
                itemDtos,
                payDtos
        );
    }

    private String toAddressJson(CustomerAddress address) {
        Map<String, Object> map = new HashMap<>();
        map.put("contactName", address.getContactName());
        map.put("mobile", address.getMobile());
        map.put("province", address.getProvince());
        map.put("city", address.getCity());
        map.put("district", address.getDistrict());
        map.put("detail", address.getDetail());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "地址序列化失败");
        }
    }

    private static String nextOrderNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "O" + ts + rnd;
    }

    private static String nextPaymentNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "P" + ts + rnd;
    }

    private static String formatYuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }
}
