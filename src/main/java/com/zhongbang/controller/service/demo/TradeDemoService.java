package com.zhongbang.controller.service.demo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.config.ZhongbangProperties;
import com.zhongbang.controller.mapper.cost.CostPriceLogMapper;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.mapper.notice.AdminNoticeMapper;
import com.zhongbang.controller.mapper.order.OrderLineMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.purchase.PurchaseItemMapper;
import com.zhongbang.controller.mapper.purchase.PurchaseOrderMapper;
import com.zhongbang.controller.mapper.purchase.SupplierLedgerMapper;
import com.zhongbang.controller.mapper.purchase.SupplierMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.mapper.stock.StockAlertStateMapper;
import com.zhongbang.controller.pojo.cost.entity.CostPriceLog;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.pojo.importdata.vo.ImportVo;
import com.zhongbang.controller.pojo.notice.entity.AdminNotice;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseItem;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseOrder;
import com.zhongbang.controller.pojo.purchase.entity.Supplier;
import com.zhongbang.controller.pojo.purchase.entity.SupplierLedger;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.service.cost.CostPriceService;
import com.zhongbang.controller.service.purchase.PurchaseService;
import com.zhongbang.controller.service.stock.StockAlertService;
import com.zhongbang.controller.service.stock.StockLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开发环境的进销存演示数据：两个供应商、一张已入库（开票）和一张草稿（不开票）、
 * 带成本的订单、一张未知成本的订单、一笔维修收入，以及一个低于最低库存的规格。
 * 生产环境开关是关的。再点一次不会重复插入。
 */
@Service
public class TradeDemoService {

    static final String SUPPLIER_NAME = "演示-华东缝配";

    private final ZhongbangProperties properties;
    private final SupplierMapper supplierMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseItemMapper purchaseItemMapper;
    private final SupplierLedgerMapper supplierLedgerMapper;
    private final ProductSkuMapper productSkuMapper;
    private final CustomerMapper customerMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final OrderLineMapper orderLineMapper;
    private final PaymentBillMapper paymentBillMapper;
    private final RepairTicketMapper repairTicketMapper;
    private final CostPriceLogMapper costPriceLogMapper;
    private final AdminNoticeMapper adminNoticeMapper;
    private final StockAlertStateMapper stockAlertStateMapper;
    private final CostPriceService costPriceService;
    private final PurchaseService purchaseService;
    private final StockAlertService stockAlertService;
    private final StockLogService stockLogService;
    private final IdentifierGenerator idGenerator;

    public TradeDemoService(
            ZhongbangProperties properties,
            SupplierMapper supplierMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            PurchaseItemMapper purchaseItemMapper,
            SupplierLedgerMapper supplierLedgerMapper,
            ProductSkuMapper productSkuMapper,
            CustomerMapper customerMapper,
            SalesOrderMapper salesOrderMapper,
            OrderLineMapper orderLineMapper,
            PaymentBillMapper paymentBillMapper,
            RepairTicketMapper repairTicketMapper,
            CostPriceLogMapper costPriceLogMapper,
            AdminNoticeMapper adminNoticeMapper,
            StockAlertStateMapper stockAlertStateMapper,
            CostPriceService costPriceService,
            PurchaseService purchaseService,
            StockAlertService stockAlertService,
            StockLogService stockLogService,
            IdentifierGenerator idGenerator
    ) {
        this.properties = properties;
        this.supplierMapper = supplierMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseItemMapper = purchaseItemMapper;
        this.supplierLedgerMapper = supplierLedgerMapper;
        this.productSkuMapper = productSkuMapper;
        this.customerMapper = customerMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.orderLineMapper = orderLineMapper;
        this.paymentBillMapper = paymentBillMapper;
        this.repairTicketMapper = repairTicketMapper;
        this.costPriceLogMapper = costPriceLogMapper;
        this.adminNoticeMapper = adminNoticeMapper;
        this.stockAlertStateMapper = stockAlertStateMapper;
        this.costPriceService = costPriceService;
        this.purchaseService = purchaseService;
        this.stockAlertService = stockAlertService;
        this.stockLogService = stockLogService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public ImportVo.DemoResult seed() {
        StaffRoles.requireBoss("只有老板可以生成演示数据");
        ensureEnabled();
        if (supplierMapper.selectCount(new LambdaQueryWrapper<Supplier>().eq(Supplier::getName, SUPPLIER_NAME)) > 0) {
            return new ImportVo.DemoResult(false, 2, 2, 2, "演示数据已经有了");
        }
        setCost(1011L, 180000L);
        setCost(1012L, 190000L);
        setCost(1021L, 80000L);
        setCost(1031L, 400000L);
        productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                .eq(ProductSku::getId, 1031L)
                .set(ProductSku::getMinStock, 8));
        stockAlertService.evaluate(1031L);

        Supplier east = supplier("演示-华东缝配", "周明", "13800002001", "标准牌", 30, "开票月结");
        Supplier local = supplier("演示-本地现货", "赵姐", "13800002002", "包缝机", 0, "现款，不开票");

        PurchaseOrder received = new PurchaseOrder();
        received.setSupplierId(east.getId());
        received.setOrderDate(DateTimes.now().toLocalDate().toString());
        received.setStatus("RECEIVED");
        received.setSource("DEMO");
        received.setInvoiced(1);
        received.setInvoiceType("增值税专用发票");
        received.setTaxRate(1300);
        received.setTaxIncluded(1);
        received.setFreightAmount(5000L);
        received.setOperatorName("演示数据");
        received.setRemark("演示：开票入库");
        PurchaseItem inLine = line(1021L, "家用多功能机 M3", "标准套装", 2, 80000L);
        received.setGoodsAmount(160000L);
        received.setTotalAmount(165000L);
        purchaseService.insertReady(received, List.of(inLine), true, CostPriceService.DEMO_REASON);

        PurchaseOrder draft = new PurchaseOrder();
        draft.setId(nextId());
        draft.setPurchaseNo("PD" + draft.getId());
        draft.setSupplierId(local.getId());
        draft.setOrderDate(DateTimes.now().toLocalDate().toString());
        draft.setStatus("DRAFT");
        draft.setSource("DEMO");
        draft.setInvoiced(0);
        draft.setFreightAmount(0L);
        draft.setGoodsAmount(410000L);
        draft.setTotalAmount(410000L);
        draft.setOperatorName("演示数据");
        draft.setRemark("演示：不开票草稿，还没入库");
        draft.setCreatedAt(DateTimes.now());
        draft.setUpdatedAt(DateTimes.now());
        purchaseOrderMapper.insert(draft);
        PurchaseItem draftLine = line(1031L, "四线包缝机 E4", "标准配置", 1, 410000L);
        draftLine.setId(nextId());
        draftLine.setPurchaseId(draft.getId());
        purchaseItemMapper.insert(draftLine);

        SupplierLedger paid = new SupplierLedger();
        paid.setSupplierId(east.getId());
        paid.setPurchaseId(received.getId());
        paid.setEntryType("PAID");
        paid.setAmount(100000L);
        paid.setMethod("BANK_TRANSFER");
        paid.setRemark("演示已付");
        paid.setOperatorName("演示数据");
        paid.setCreatedAt(DateTimes.now());
        supplierLedgerMapper.insert(paid);

        Customer customer = new Customer();
        customer.setTenantId(1L);
        customer.setName("演示门店");
        customer.setMobile("13900006601");
        customer.setLevel(1);
        customer.setLevelCode("NORMAL");
        customer.setLevelManual(0);
        customer.setCustomerType("WORKSHOP");
        customer.setTotalPaid(0L);
        customer.setSource("DEMO_TRADE");
        customer.setDeleted(0);
        customer.setCreatedAt(DateTimes.now());
        customer.setUpdatedAt(DateTimes.now());
        customerMapper.insert(customer);

        order(customer, true, 1011L, "电脑平缝机 A5", "标准台板 + 伺服电机", 328000L, 180000L, 20000L, 100000L);
        order(customer, false, 1041L, "电脑绣花机 12 头", "12 头标准型", 6800000L, null, 0L, 6800000L);

        repair(customer, true, 15000L);
        repair(customer, false, 8000L);

        return new ImportVo.DemoResult(true, 2, 2, 2, "已生成供应商、采购单、进价、开票和不开票的订单，以及一条低库存");
    }

    @Transactional
    public ImportVo.DemoResult clear() {
        StaffRoles.requireBoss("只有老板可以清除演示数据");
        ensureEnabled();
        List<SalesOrder> orders = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrder>().eq(SalesOrder::getSource, "DEMO_TRADE"));
        for (SalesOrder order : orders) {
            List<OrderLine> lines = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLine>().eq(OrderLine::getOrderId, order.getId()));
            for (OrderLine line : lines) {
                int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
                productSkuMapper.restoreStock(line.getSkuId(), quantity);
                stockLogService.record(line.getSkuId(), StockLogService.SALE_CANCEL, order.getId(), quantity, null);
                orderLineMapper.deleteById(line.getId());
            }
            paymentBillMapper.delete(new LambdaQueryWrapper<PaymentBill>().eq(PaymentBill::getBizId, order.getId()).eq(PaymentBill::getBizType, "ORDER"));
            salesOrderMapper.deleteById(order.getId());
        }
        List<PurchaseOrder> purchases = purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getSource, "DEMO"));
        for (PurchaseOrder purchase : purchases) {
            if ("RECEIVED".equals(purchase.getStatus())) {
                purchaseService.voidPurchase(purchase.getId(), null);
            }
            purchaseItemMapper.delete(new LambdaQueryWrapper<PurchaseItem>().eq(PurchaseItem::getPurchaseId, purchase.getId()));
            supplierLedgerMapper.delete(new LambdaQueryWrapper<SupplierLedger>().eq(SupplierLedger::getPurchaseId, purchase.getId()));
            purchaseOrderMapper.deleteById(purchase.getId());
        }
        supplierLedgerMapper.delete(new LambdaQueryWrapper<SupplierLedger>().eq(SupplierLedger::getOperatorName, "演示数据"));
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>().eq(Supplier::getSource, "DEMO"));
        for (Supplier supplier : suppliers) {
            supplierMapper.deleteById(supplier.getId());
        }
        List<Customer> demoCustomers = customerMapper.selectList(new LambdaQueryWrapper<Customer>().eq(Customer::getSource, "DEMO_TRADE"));
        for (Customer demoCustomer : demoCustomers) {
            repairTicketMapper.delete(new LambdaQueryWrapper<RepairTicket>().eq(RepairTicket::getCustomerId, demoCustomer.getId()));
        }
        customerMapper.delete(new LambdaQueryWrapper<Customer>().eq(Customer::getSource, "DEMO_TRADE"));
        costPriceLogMapper.delete(new LambdaQueryWrapper<CostPriceLog>().eq(CostPriceLog::getReason, CostPriceService.DEMO_REASON));
        costPriceService.rebuildFromRemainingLogs();
        productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>().eq(ProductSku::getId, 1031L).set(ProductSku::getMinStock, null));
        stockAlertStateMapper.deleteById(1031L);
        adminNoticeMapper.delete(new LambdaQueryWrapper<AdminNotice>().eq(AdminNotice::getEventType, "STOCK_LOW").eq(AdminNotice::getBizId, 1031L));
        return new ImportVo.DemoResult(false, 0, 0, 0, "已清除演示进销存数据");
    }

    private void setCost(long skuId, long cost) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            return;
        }
        costPriceService.change(sku, cost, "演示数据", CostPriceService.DEMO_REASON);
    }

    private Supplier supplier(String name, String contact, String mobile, String brands, int days, String remark) {
        Supplier row = new Supplier();
        row.setName(name);
        row.setContact(contact);
        row.setMobile(mobile);
        row.setBrands(brands);
        row.setAccountDays(days);
        row.setRemark(remark);
        row.setSource("DEMO");
        row.setDeleted(0);
        row.setCreatedAt(DateTimes.now());
        row.setUpdatedAt(DateTimes.now());
        supplierMapper.insert(row);
        return row;
    }

    private PurchaseItem line(long skuId, String productName, String skuName, int qty, long cost) {
        PurchaseItem line = new PurchaseItem();
        line.setSkuId(skuId);
        line.setProductName(productName);
        line.setSkuName(skuName);
        line.setQuantity(qty);
        line.setUnitCost(cost);
        line.setLineAmount(cost * qty);
        return line;
    }

    private void order(Customer customer, boolean invoiced, long skuId, String productName, String skuName,
                       long price, Long cost, long install, long paid) {
        SalesOrder order = new SalesOrder();
        order.setId(nextId());
        order.setTenantId(1L);
        order.setOrderNo("DT" + order.getId());
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getName());
        order.setCustomerMobile(customer.getMobile());
        order.setStatus(paid >= price + install ? "PAID" : "PARTIALLY_PAID");
        order.setSource("DEMO_TRADE");
        order.setDeleted(0);
        order.setDepositAmount(0L);
        order.setFreightAmount(0L);
        order.setAdjustAmount(0L);
        order.setPriceAdjusted(0);
        order.setGoodsAmount(price);
        order.setInstallAmount(install);
        order.setPayableAmount(price + install);
        order.setTotalAmount(price + install);
        order.setPaidAmount(paid);
        order.setBalanceAmount(Math.max(0, price + install - paid));
        order.setAddressText("演示地址");
        order.setInvoiced(invoiced ? 1 : 0);
        if (invoiced) {
            order.setInvoiceType("增值税普通发票");
            order.setTaxRate(1300);
            order.setTaxIncluded(1);
        }
        LocalDateTime now = DateTimes.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        salesOrderMapper.insert(order);
        productSkuMapper.deductStock(skuId, 1);
        stockLogService.record(skuId, StockLogService.SALE_OUT, order.getId(), -1, null);
        OrderLine line = new OrderLine();
        line.setId(nextId());
        line.setOrderId(order.getId());
        line.setSkuId(skuId);
        line.setProductName(productName);
        line.setSkuName(skuName);
        line.setQuantity(1);
        line.setUnitPrice(price);
        line.setLineAmount(price);
        line.setDeliveryMethod("STORE_PICKUP");
        line.setInstallFee(install);
        if (cost == null) {
            line.setCostKnown(0);
        } else {
            line.setCostKnown(1);
            line.setCostPrice(cost);
            line.setCostAmount(cost);
        }
        orderLineMapper.insert(line);
        stockAlertService.evaluate(skuId);
    }

    private void repair(Customer customer, boolean invoiced, long paid) {
        RepairTicket ticket = new RepairTicket();
        ticket.setId(nextId());
        ticket.setTenantId(1L);
        ticket.setTicketNo("RD" + ticket.getId());
        ticket.setCustomerId(customer.getId());
        ticket.setCustomerName(customer.getName());
        ticket.setStatus("DONE");
        ticket.setContactMobile(customer.getMobile());
        ticket.setQuoteAmount(paid);
        ticket.setPayableAmount(paid);
        ticket.setPaidAmount(paid);
        ticket.setInWarranty(0);
        ticket.setInvoiced(invoiced ? 1 : 0);
        if (invoiced) {
            ticket.setInvoiceType("增值税普通发票");
            ticket.setTaxRate(1300);
            ticket.setTaxIncluded(1);
        }
        ticket.setCreatedAt(DateTimes.now());
        ticket.setUpdatedAt(DateTimes.now());
        repairTicketMapper.insert(ticket);
    }

    private void ensureEnabled() {
        if (!properties.getDemo().isEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前环境没有打开演示数据");
        }
    }

    private long nextId() {
        return idGenerator.nextId(null).longValue();
    }
}
