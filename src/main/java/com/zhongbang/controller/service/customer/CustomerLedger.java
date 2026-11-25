package com.zhongbang.controller.service.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.mapper.customer.RepayAgreementMapper;
import com.zhongbang.controller.mapper.order.OrderLineMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.repair.RepairLineMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.pojo.customer.entity.RepayAgreement;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.repair.entity.RepairLine;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把订单、报修、付款和商品读进内存，再按客户算金额。
 * 经销店的客户量不大，这样写比一条很长的 SQL 更容易看懂。
 * 取消的订单和报修不计入金额。待付款 = 应付减已付，余款还挂在订单上，所以会算进去。
 */
@Component
public class CustomerLedger {

    private final SalesOrderMapper orderMapper;
    private final OrderLineMapper orderLineMapper;
    private final RepairTicketMapper repairTicketMapper;
    private final RepairLineMapper repairLineMapper;
    private final PaymentBillMapper paymentBillMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final RepayAgreementMapper repayAgreementMapper;

    public CustomerLedger(
            SalesOrderMapper orderMapper,
            OrderLineMapper orderLineMapper,
            RepairTicketMapper repairTicketMapper,
            RepairLineMapper repairLineMapper,
            PaymentBillMapper paymentBillMapper,
            ProductMapper productMapper,
            ProductSkuMapper productSkuMapper,
            RepayAgreementMapper repayAgreementMapper
    ) {
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.repairTicketMapper = repairTicketMapper;
        this.repairLineMapper = repairLineMapper;
        this.paymentBillMapper = paymentBillMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.repayAgreementMapper = repayAgreementMapper;
    }

    public Snapshot load() {
        List<SalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<>());
        List<OrderLine> lines = orderLineMapper.selectList(new LambdaQueryWrapper<>());
        List<RepairTicket> repairs = repairTicketMapper.selectList(new LambdaQueryWrapper<>());
        List<RepairLine> repairLines = repairLineMapper.selectList(new LambdaQueryWrapper<>());
        List<PaymentBill> payments = paymentBillMapper.selectList(new LambdaQueryWrapper<>());
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<>());
        List<ProductSku> skus = productSkuMapper.selectList(new LambdaQueryWrapper<>());
        List<RepayAgreement> agreements = repayAgreementMapper.selectList(new LambdaQueryWrapper<RepayAgreement>()
                .eq(RepayAgreement::getStatus, "ACTIVE"));
        return new Snapshot(orders, lines, repairs, repairLines, payments, products, skus, agreements, LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    /**
     * 一次查询的快照。后面的方法只读这些列表，不再访问数据库。
     */
    public static final class Snapshot {
        private final Map<Long, List<SalesOrder>> orders = new HashMap<>();
        private final Map<Long, List<RepairTicket>> repairs = new HashMap<>();
        private final Map<Long, List<OrderLine>> linesByOrder = new HashMap<>();
        private final Map<Long, List<RepairLine>> repairLinesByTicket = new HashMap<>();
        private final Map<Long, List<PaymentBill>> paymentsByOrder = new HashMap<>();
        private final Map<Long, List<PaymentBill>> paymentsByRepair = new HashMap<>();
        private final Map<String, Product> productByName = new HashMap<>();
        private final Map<Long, Product> productBySku = new HashMap<>();
        private final Map<Long, Integer> repairCountByAsset = new HashMap<>();
        private final Map<Long, Boolean> overdue = new HashMap<>();
        private final LocalDate today;
        private final List<SalesOrder> allOrders;
        private final List<RepairTicket> allRepairs;

        Snapshot(
                List<SalesOrder> orders,
                List<OrderLine> lines,
                List<RepairTicket> repairs,
                List<RepairLine> repairLines,
                List<PaymentBill> payments,
                List<Product> products,
                List<ProductSku> skus,
                List<RepayAgreement> agreements,
                LocalDate today
        ) {
            this.today = today;
            this.allOrders = orders;
            this.allRepairs = repairs;
            for (SalesOrder order : orders) {
                if (order.getCustomerId() != null) {
                    this.orders.computeIfAbsent(order.getCustomerId(), key -> new ArrayList<>()).add(order);
                }
            }
            for (RepairTicket ticket : repairs) {
                if (ticket.getCustomerId() != null) {
                    this.repairs.computeIfAbsent(ticket.getCustomerId(), key -> new ArrayList<>()).add(ticket);
                }
            }
            for (OrderLine line : lines) {
                linesByOrder.computeIfAbsent(line.getOrderId(), key -> new ArrayList<>()).add(line);
            }
            for (RepairLine line : repairLines) {
                repairLinesByTicket.computeIfAbsent(line.getTicketId(), key -> new ArrayList<>()).add(line);
                if (line.getAssetId() != null) {
                    repairCountByAsset.merge(line.getAssetId(), 1, Integer::sum);
                }
            }
            for (PaymentBill payment : payments) {
                if ("REPAIR".equals(payment.getBizType())) {
                    Long id = payment.getBizId() != null ? payment.getBizId() : payment.getOrderId();
                    paymentsByRepair.computeIfAbsent(id, key -> new ArrayList<>()).add(payment);
                } else {
                    Long id = payment.getBizId() != null ? payment.getBizId() : payment.getOrderId();
                    paymentsByOrder.computeIfAbsent(id, key -> new ArrayList<>()).add(payment);
                }
            }
            Map<Long, Product> byId = new HashMap<>();
            for (Product product : products) {
                byId.put(product.getId(), product);
                if (product.getName() != null) {
                    productByName.putIfAbsent(product.getName(), product);
                }
            }
            for (ProductSku sku : skus) {
                Product product = byId.get(sku.getProductId());
                if (product != null) {
                    productBySku.put(sku.getId(), product);
                }
            }
            for (RepayAgreement agreement : agreements) {
                if (agreement.getDueDate() != null && agreement.getDueDate().isBefore(today)) {
                    overdue.put(agreement.getCustomerId(), true);
                }
            }
        }

        public LocalDate today() {
            return today;
        }

        public List<SalesOrder> ordersOf(Long customerId) {
            return orders.getOrDefault(customerId, List.of());
        }

        public List<RepairTicket> repairsOf(Long customerId) {
            return repairs.getOrDefault(customerId, List.of());
        }

        public List<OrderLine> linesOf(Long orderId) {
            return linesByOrder.getOrDefault(orderId, List.of());
        }

        public List<RepairLine> repairLinesOf(Long ticketId) {
            return repairLinesByTicket.getOrDefault(ticketId, List.of());
        }

        public int repairCount(Long assetId) {
            return assetId == null ? 0 : repairCountByAsset.getOrDefault(assetId, 0);
        }

        public boolean overdue(Long customerId) {
            return Boolean.TRUE.equals(overdue.get(customerId));
        }

        public Product productOf(OrderLine line) {
            if (line.getSkuId() != null && productBySku.containsKey(line.getSkuId())) {
                return productBySku.get(line.getSkuId());
            }
            if (line.getProductName() != null) {
                return productByName.get(line.getProductName());
            }
            return null;
        }

        /** 累计实收：未取消订单的已付 + 未取消报修的已付。等级升级用这个数。 */
        public Map<Long, Long> receivedByCustomer() {
            Map<Long, Long> sums = new HashMap<>();
            for (SalesOrder order : allOrders) {
                if (order.getCustomerId() == null || CustomerRules.cancelled(order.getStatus())) {
                    continue;
                }
                sums.merge(order.getCustomerId(), order.getPaidAmount() == null ? 0L : order.getPaidAmount(), Long::sum);
            }
            for (RepairTicket ticket : allRepairs) {
                if (ticket.getCustomerId() == null || CustomerRules.cancelled(ticket.getStatus())) {
                    continue;
                }
                sums.merge(ticket.getCustomerId(), ticket.getPaidAmount() == null ? 0L : ticket.getPaidAmount(), Long::sum);
            }
            return sums;
        }

        public Facts facts(Long customerId) {
            long orderAmount = 0;
            long received = 0;
            long unpaid = 0;
            int count = 0;
            LocalDateTime first = null;
            LocalDateTime last = null;
            for (SalesOrder order : ordersOf(customerId)) {
                if (CustomerRules.cancelled(order.getStatus())) {
                    continue;
                }
                count++;
                long payable = order.getPayableAmount() == null ? 0L : order.getPayableAmount();
                long paid = order.getPaidAmount() == null ? 0L : order.getPaidAmount();
                orderAmount += payable;
                received += paid;
                unpaid += CustomerRules.unpaid(payable, paid);
                LocalDateTime created = order.getCreatedAt();
                if (created != null && (first == null || created.isBefore(first))) {
                    first = created;
                }
                if (created != null && (last == null || created.isAfter(last))) {
                    last = created;
                }
            }
            for (RepairTicket ticket : repairsOf(customerId)) {
                if (CustomerRules.cancelled(ticket.getStatus())) {
                    continue;
                }
                received += ticket.getPaidAmount() == null ? 0L : ticket.getPaidAmount();
                unpaid += CustomerRules.unpaid(ticket.getPayableAmount(), ticket.getPaidAmount());
            }
            Long avg = count == 0 ? null : orderAmount / count;
            Long days = last == null ? null : ChronoUnit.DAYS.between(last.toLocalDate(), today);
            return new Facts(orderAmount, received, unpaid, count, avg, first, last, days);
        }

        public List<ModelRow> models(Long customerId) {
            Map<String, ModelRow> rows = new LinkedHashMap<>();
            for (SalesOrder order : ordersOf(customerId)) {
                if (CustomerRules.cancelled(order.getStatus())) {
                    continue;
                }
                for (OrderLine line : linesOf(order.getId())) {
                    Product product = productOf(line);
                    String model = product != null && product.getModel() != null && !product.getModel().isBlank()
                            ? product.getModel()
                            : (line.getProductName() == null ? "未命名机型" : line.getProductName());
                    String brand = product == null || product.getBrand() == null ? "" : product.getBrand();
                    String category = product == null || product.getCategory() == null ? "" : product.getCategory();
                    ModelRow row = rows.computeIfAbsent(model, key -> new ModelRow(model, brand, category));
                    row.times++;
                    row.quantity += line.getQuantity() == null ? 0 : line.getQuantity();
                    row.amount += line.getLineAmount() == null ? 0L : line.getLineAmount();
                    if (order.getCreatedAt() != null && (row.last == null || order.getCreatedAt().isAfter(row.last))) {
                        row.last = order.getCreatedAt();
                    }
                }
            }
            return rows.values().stream().sorted(Comparator.comparingLong((ModelRow row) -> row.amount).reversed()).toList();
        }

        public PreferenceAuto preference(Long customerId) {
            Map<String, Integer> brands = new HashMap<>();
            Map<String, Integer> categories = new HashMap<>();
            Map<String, Integer> machineBands = new HashMap<>();
            Map<String, Integer> allBands = new HashMap<>();
            Map<String, Integer> pays = new HashMap<>();
            int machine = 0;
            int install = 0;
            boolean anyLine = false;
            for (SalesOrder order : ordersOf(customerId)) {
                if (CustomerRules.cancelled(order.getStatus())) {
                    continue;
                }
                for (OrderLine line : linesOf(order.getId())) {
                    anyLine = true;
                    Product product = productOf(line);
                    if (product != null && product.getBrand() != null && !product.getBrand().isBlank()) {
                        brands.merge(product.getBrand(), 1, Integer::sum);
                    }
                    if (product != null && product.getCategory() != null && !product.getCategory().isBlank()) {
                        categories.merge(product.getCategory(), 1, Integer::sum);
                    }
                    long unit = line.getUnitPrice() == null ? 0L : line.getUnitPrice();
                    boolean machineLine = unit >= 100_000L || "ON_SITE_INSTALL".equals(line.getDeliveryMethod());
                    if (machineLine) {
                        machine++;
                    }
                    if ("ON_SITE_INSTALL".equals(line.getDeliveryMethod())) {
                        install++;
                    }
                    if (unit > 0) {
                        allBands.merge(CustomerRules.priceBand(unit), 1, Integer::sum);
                        if (unit >= 100_000L) {
                            machineBands.merge(CustomerRules.priceBand(unit), 1, Integer::sum);
                        }
                    }
                }
                for (PaymentBill payment : paymentsByOrder.getOrDefault(order.getId(), List.of())) {
                    if ("SUCCESS".equals(payment.getStatus())) {
                        pays.merge(CustomerRules.payMethodLabel(payment.getOfflineMethod(), payment.getChannel()), 1, Integer::sum);
                    }
                }
            }
            for (RepairTicket ticket : repairsOf(customerId)) {
                for (PaymentBill payment : paymentsByRepair.getOrDefault(ticket.getId(), List.of())) {
                    if ("SUCCESS".equals(payment.getStatus())) {
                        pays.merge(CustomerRules.payMethodLabel(payment.getOfflineMethod(), payment.getChannel()), 1, Integer::sum);
                    }
                }
            }
            Boolean needInstall = !anyLine ? null : machine > 0 && install * 2 >= machine;
            String band = top(machineBands.isEmpty() ? allBands : machineBands);
            return new PreferenceAuto(top(brands), top(categories), band, top(pays), needInstall);
        }

        /** 沉睡：最近一笔未取消订单早于 180 天，或者从没下过单且建档已超过 180 天。 */
        public boolean sleeping(Long customerId, LocalDateTime createdAt) {
            Facts facts = facts(customerId);
            if (facts.lastOrderAt() != null) {
                return ChronoUnit.DAYS.between(facts.lastOrderAt().toLocalDate(), today) > 180;
            }
            if (createdAt == null) {
                return false;
            }
            return ChronoUnit.DAYS.between(createdAt.toLocalDate(), today) > 180;
        }

        private static String top(Map<String, Integer> counts) {
            return counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }

    public record Facts(
            long orderAmount,
            long receivedAmount,
            long unpaidAmount,
            int orderCount,
            Long avgAmount,
            LocalDateTime firstOrderAt,
            LocalDateTime lastOrderAt,
            Long daysSinceLast
    ) {
    }

    public static final class ModelRow {
        public final String model;
        public final String brand;
        public final String category;
        public int times;
        public int quantity;
        public long amount;
        public LocalDateTime last;

        ModelRow(String model, String brand, String category) {
            this.model = model;
            this.brand = brand;
            this.category = category;
        }
    }

    public record PreferenceAuto(String brand, String category, String priceBand, String payMethod, Boolean needInstall) {
    }
}
