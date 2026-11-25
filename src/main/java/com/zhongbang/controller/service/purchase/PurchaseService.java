package com.zhongbang.controller.service.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.purchase.PurchaseItemMapper;
import com.zhongbang.controller.mapper.purchase.PurchaseOrderMapper;
import com.zhongbang.controller.mapper.purchase.SupplierLedgerMapper;
import com.zhongbang.controller.mapper.purchase.SupplierMapper;
import com.zhongbang.controller.mapper.stock.StockLogMapper;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.purchase.dto.PurchaseRequests;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseItem;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseOrder;
import com.zhongbang.controller.pojo.purchase.entity.Supplier;
import com.zhongbang.controller.pojo.purchase.entity.SupplierLedger;
import com.zhongbang.controller.pojo.purchase.vo.PurchaseVo;
import com.zhongbang.controller.pojo.stock.entity.StockLog;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.service.cost.CostPriceService;
import com.zhongbang.controller.service.stock.StockAlertService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商和采购入库。
 * 管理员可以记草稿、确认入库和作废，但请求里的进价和运费会被丢掉，返回里也不带金额。
 * 老板才能填进价。确认入库后加库存、写流水，并按设置更新进价。作废已入库的单子把库存减回去。
 */
@Service
public class PurchaseService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final SupplierMapper supplierMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseItemMapper purchaseItemMapper;
    private final SupplierLedgerMapper supplierLedgerMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final StockLogMapper stockLogMapper;
    private final CostPriceService costPriceService;
    private final StockAlertService stockAlertService;
    private final IdentifierGenerator idGenerator;

    public PurchaseService(
            SupplierMapper supplierMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            PurchaseItemMapper purchaseItemMapper,
            SupplierLedgerMapper supplierLedgerMapper,
            ProductMapper productMapper,
            ProductSkuMapper productSkuMapper,
            StockLogMapper stockLogMapper,
            CostPriceService costPriceService,
            StockAlertService stockAlertService,
            IdentifierGenerator idGenerator
    ) {
        this.supplierMapper = supplierMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseItemMapper = purchaseItemMapper;
        this.supplierLedgerMapper = supplierLedgerMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.stockLogMapper = stockLogMapper;
        this.costPriceService = costPriceService;
        this.stockAlertService = stockAlertService;
        this.idGenerator = idGenerator;
    }

    public PageResult<PurchaseVo.SupplierItem> listSuppliers(String keyword, int page, int size) {
        UserContext.require();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>().orderByDesc(Supplier::getId);
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(query -> query.like(Supplier::getName, like)
                    .or().like(Supplier::getContact, like)
                    .or().like(Supplier::getMobile, like)
                    .or().like(Supplier::getBrands, like));
        }
        Page<Supplier> result = supplierMapper.selectPage(new Page<>(p, s), wrapper);
        boolean boss = boss();
        Map<Long, long[]> totals = boss ? ledgerTotals(result.getRecords().stream().map(Supplier::getId).toList()) : Map.of();
        List<PurchaseVo.SupplierItem> list = result.getRecords().stream().map(row -> toSupplier(row, boss, totals)).toList();
        return PageResult.of(list, result.getTotal(), p, s);
    }

    @Transactional
    public PurchaseVo.SupplierItem saveSupplier(Long id, PurchaseRequests.SupplierSave request) {
        UserContext.require();
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写供应商名称");
        }
        if (request.getAccountDays() != null && request.getAccountDays() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账期不能为负");
        }
        Supplier row = id == null ? new Supplier() : requireSupplier(id);
        if (id == null) {
            row.setSource("MANUAL");
            row.setDeleted(0);
            row.setCreatedAt(DateTimes.now());
        }
        row.setName(request.getName().trim());
        row.setContact(blank(request.getContact()));
        row.setMobile(blank(request.getMobile()));
        row.setBrands(blank(request.getBrands()));
        row.setAccountDays(request.getAccountDays());
        row.setRemark(blank(request.getRemark()));
        row.setUpdatedAt(DateTimes.now());
        if (id == null) {
            supplierMapper.insert(row);
        } else {
            supplierMapper.updateById(row);
        }
        boolean boss = boss();
        return toSupplier(row, boss, boss ? ledgerTotals(List.of(row.getId())) : Map.of());
    }

    @Transactional
    public void deleteSupplier(long id) {
        UserContext.require();
        requireSupplier(id);
        long used = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getSupplierId, id)
                .ne(PurchaseOrder::getStatus, "VOID"));
        if (used > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "还有未作废的采购单，不能删除供应商");
        }
        supplierMapper.deleteById(id);
    }

    public PurchaseVo.Ledger ledger(long supplierId) {
        StaffRoles.requireBoss("只有老板可以查看供应商应付");
        Supplier supplier = requireSupplier(supplierId);
        List<SupplierLedger> rows = supplierLedgerMapper.selectList(new LambdaQueryWrapper<SupplierLedger>()
                .eq(SupplierLedger::getSupplierId, supplierId)
                .orderByDesc(SupplierLedger::getCreatedAt));
        long[] totals = sum(rows);
        return new PurchaseVo.Ledger(
                supplier.getId(),
                supplier.getName(),
                totals[0],
                totals[1],
                totals[0] - totals[1] - totals[2],
                rows.stream().map(this::toLedger).toList()
        );
    }

    @Transactional
    public PurchaseVo.Ledger pay(long supplierId, PurchaseRequests.Payment request) {
        StaffRoles.requireBoss("只有老板可以登记供应商付款");
        requireSupplier(supplierId);
        if (request == null || request.getAmount() == null || request.getAmount() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写付款金额");
        }
        SupplierLedger row = new SupplierLedger();
        row.setSupplierId(supplierId);
        row.setEntryType("PAID");
        row.setAmount(request.getAmount());
        row.setMethod(blank(request.getMethod()));
        row.setRemark(blank(request.getRemark()));
        row.setOperatorName(UserContext.require().getDisplayName());
        row.setCreatedAt(DateTimes.now());
        supplierLedgerMapper.insert(row);
        return ledger(supplierId);
    }

    public PageResult<PurchaseVo.Detail> listPurchases(String status, String keyword, int page, int size) {
        UserContext.require();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>().orderByDesc(PurchaseOrder::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(PurchaseOrder::getStatus, status.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PurchaseOrder::getPurchaseNo, keyword.trim());
        }
        Page<PurchaseOrder> result = purchaseOrderMapper.selectPage(new Page<>(p, s), wrapper);
        boolean boss = boss();
        List<PurchaseVo.Detail> list = result.getRecords().stream().map(row -> toDetail(row, itemsOf(row.getId()), boss)).toList();
        return PageResult.of(list, result.getTotal(), p, s);
    }

    public PurchaseVo.Detail getPurchase(long id) {
        UserContext.require();
        PurchaseOrder order = requirePurchase(id);
        return toDetail(order, itemsOf(id), boss());
    }

    @Transactional
    public PurchaseVo.Detail savePurchase(Long id, PurchaseRequests.PurchaseSave request) {
        UserContext.require();
        boolean boss = boss();
        if (request == null || request.getSupplierId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择供应商");
        }
        requireSupplier(request.getSupplierId());
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少填写一行明细");
        }
        PurchaseOrder order = id == null ? new PurchaseOrder() : requirePurchase(id);
        if (id != null && !"DRAFT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "只有草稿可以修改");
        }
        if (id == null) {
            order.setPurchaseNo(nextPurchaseNo());
            order.setStatus("DRAFT");
            order.setSource("MANUAL");
            order.setCreatedAt(DateTimes.now());
            order.setFreightAmount(0L);
        }
        order.setSupplierId(request.getSupplierId());
        order.setOrderDate(blank(request.getOrderDate()) == null ? LocalDate.now(SHANGHAI).toString() : request.getOrderDate().trim());
        applyInvoice(order, request.getInvoiced(), request.getInvoiceType(), request.getTaxRate(), request.getTaxIncluded());
        order.setAttachmentUrls(Jsons.writeStrings(request.getAttachmentUrls()));
        order.setRemark(blank(request.getRemark()));
        order.setOperatorName(UserContext.require().getDisplayName());
        order.setUpdatedAt(DateTimes.now());
        long freight = order.getFreightAmount() == null ? 0L : order.getFreightAmount();
        if (boss) {
            if (request.getFreightAmount() != null && request.getFreightAmount() < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "运费不能为负");
            }
            freight = request.getFreightAmount() == null ? 0L : request.getFreightAmount();
        }
        order.setFreightAmount(freight);
        List<PurchaseItem> lines = buildLines(request.getLines(), order.getId(), boss, id == null ? List.of() : itemsOf(id));
        long goods = 0L;
        boolean priced = true;
        for (PurchaseItem line : lines) {
            if (line.getLineAmount() == null) {
                priced = false;
            } else {
                goods += line.getLineAmount();
            }
        }
        order.setGoodsAmount(goods);
        order.setTotalAmount(priced ? goods + freight : goods + freight);
        if (id == null) {
            purchaseOrderMapper.insert(order);
        } else {
            purchaseOrderMapper.updateById(order);
            purchaseItemMapper.delete(new LambdaQueryWrapper<PurchaseItem>().eq(PurchaseItem::getPurchaseId, order.getId()));
        }
        for (PurchaseItem line : lines) {
            line.setPurchaseId(order.getId());
            purchaseItemMapper.insert(line);
        }
        return toDetail(order, lines, boss);
    }

    /** 给导入和演示数据直接落下已经算好的采购单。applyStock 为真时才改库存和进价。 */
    @Transactional
    public PurchaseOrder insertReady(PurchaseOrder order, List<PurchaseItem> lines, boolean applyStock, String reason) {
        if (order.getId() == null) {
            order.setId(nextId());
        }
        if (order.getPurchaseNo() == null || order.getPurchaseNo().isBlank()) {
            order.setPurchaseNo(nextPurchaseNo());
        }
        order.setCreatedAt(order.getCreatedAt() == null ? DateTimes.now() : order.getCreatedAt());
        order.setUpdatedAt(DateTimes.now());
        purchaseOrderMapper.insert(order);
        for (PurchaseItem line : lines) {
            line.setPurchaseId(order.getId());
            if (line.getId() == null) {
                line.setId(nextId());
            }
            purchaseItemMapper.insert(line);
        }
        if ("RECEIVED".equals(order.getStatus()) && applyStock) {
            receiveStored(order, lines, reason);
        } else if ("RECEIVED".equals(order.getStatus())) {
            writePayable(order, reason);
        }
        return order;
    }

    @Transactional
    public PurchaseVo.Detail receive(long id) {
        UserContext.require();
        PurchaseOrder order = requirePurchase(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "只有草稿可以入库");
        }
        List<PurchaseItem> lines = itemsOf(id);
        boolean boss = boss();
        if (boss) {
            for (PurchaseItem line : lines) {
                if (line.getUnitCost() == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "请先填写每行进价再入库");
                }
            }
        }
        receiveStored(order, lines, null);
        return toDetail(requirePurchase(id), itemsOf(id), boss);
    }

    @Transactional
    public PurchaseVo.Detail voidPurchase(long id) {
        UserContext.require();
        PurchaseOrder order = requirePurchase(id);
        if ("VOID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "采购单已经作废");
        }
        List<PurchaseItem> lines = itemsOf(id);
        if ("RECEIVED".equals(order.getStatus())) {
            String reason = "DEMO".equals(order.getSource()) ? CostPriceService.DEMO_REASON : null;
            List<PurchaseItem> reversed = new ArrayList<>(lines);
            java.util.Collections.reverse(reversed);
            for (PurchaseItem line : reversed) {
                int qty = line.getQuantity() == null ? 0 : line.getQuantity();
                if (productSkuMapper.deductStock(line.getSkuId(), qty) != 1) {
                    throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT, "库存不足，无法作废这张入库单");
                }
                ProductSku sku = productSkuMapper.selectById(line.getSkuId());
                writeStockLog(line.getSkuId(), "PURCHASE_VOID", order.getId(), -qty, sku.getStock(), line.getUnitCost());
                boolean latest = isLatestInbound(line.getSkuId(), order.getId());
                costPriceService.rollbackInbound(
                        line.getSkuId(),
                        qty,
                        line.getUnitCost(),
                        line.getBeforeCost(),
                        line.getBeforeStock() == null ? 0 : line.getBeforeStock(),
                        order.getCostMethod(),
                        latest,
                        UserContext.require().getDisplayName(),
                        reason
                );
                stockAlertService.evaluate(line.getSkuId());
            }
            long total = order.getTotalAmount() == null ? 0L : order.getTotalAmount();
            if (total > 0) {
                SupplierLedger ledger = new SupplierLedger();
                ledger.setSupplierId(order.getSupplierId());
                ledger.setPurchaseId(order.getId());
                ledger.setEntryType("VOID");
                ledger.setAmount(total);
                ledger.setRemark("作废采购单 " + order.getPurchaseNo());
                ledger.setOperatorName(UserContext.require().getDisplayName());
                ledger.setCreatedAt(DateTimes.now());
                supplierLedgerMapper.insert(ledger);
            }
        }
        order.setStatus("VOID");
        order.setUpdatedAt(DateTimes.now());
        order.setOperatorName(UserContext.require().getDisplayName());
        purchaseOrderMapper.updateById(order);
        return toDetail(order, lines, boss());
    }

    private void receiveStored(PurchaseOrder order, List<PurchaseItem> lines, String reason) {
        String method = costPriceService.normalizeMethod(costPriceService.setting().getCostMethod());
        String operator = order.getOperatorName() == null ? "系统" : order.getOperatorName();
        try {
            operator = UserContext.require().getDisplayName();
        } catch (RuntimeException ignored) {
            // 演示数据和导入在没有登录用户时用单子上的名字。
        }
        for (PurchaseItem line : lines) {
            ProductSku before = productSkuMapper.selectById(line.getSkuId());
            int beforeStock = before.getStock() == null ? 0 : before.getStock();
            line.setBeforeCost(before.getCostPrice());
            line.setBeforeStock(beforeStock);
            int qty = line.getQuantity() == null ? 0 : line.getQuantity();
            productSkuMapper.restoreStock(line.getSkuId(), qty);
            ProductSku after = productSkuMapper.selectById(line.getSkuId());
            writeStockLog(line.getSkuId(), "PURCHASE_IN", order.getId(), qty, after.getStock(), line.getUnitCost());
            if (line.getUnitCost() != null) {
                costPriceService.applyInbound(line.getSkuId(), qty, line.getUnitCost(), operator, reason);
            }
            purchaseItemMapper.updateById(line);
            stockAlertService.evaluate(line.getSkuId());
        }
        order.setStatus("RECEIVED");
        order.setCostMethod(method);
        order.setUpdatedAt(DateTimes.now());
        order.setOperatorName(operator);
        purchaseOrderMapper.updateById(order);
        writePayable(order, reason);
    }

    private void writePayable(PurchaseOrder order, String reason) {
        long total = order.getTotalAmount() == null ? 0L : order.getTotalAmount();
        boolean anyCost = itemsOf(order.getId()).stream().anyMatch(line -> line.getUnitCost() != null);
        if (total <= 0 && !anyCost) {
            return;
        }
        if (total <= 0) {
            return;
        }
        long exists = supplierLedgerMapper.selectCount(new LambdaQueryWrapper<SupplierLedger>()
                .eq(SupplierLedger::getPurchaseId, order.getId())
                .eq(SupplierLedger::getEntryType, "PAYABLE"));
        if (exists > 0) {
            return;
        }
        SupplierLedger ledger = new SupplierLedger();
        ledger.setSupplierId(order.getSupplierId());
        ledger.setPurchaseId(order.getId());
        ledger.setEntryType("PAYABLE");
        ledger.setAmount(total);
        ledger.setRemark(reason);
        ledger.setOperatorName(order.getOperatorName());
        ledger.setCreatedAt(DateTimes.now());
        supplierLedgerMapper.insert(ledger);
    }

    private boolean isLatestInbound(long skuId, long purchaseId) {
        StockLog later = stockLogMapper.selectOne(new LambdaQueryWrapper<StockLog>()
                .eq(StockLog::getSkuId, skuId)
                .eq(StockLog::getBizType, "PURCHASE_IN")
                .ne(StockLog::getBizId, purchaseId)
                .orderByDesc(StockLog::getCreatedAt)
                .last("limit 1"));
        if (later == null) {
            return true;
        }
        StockLog mine = stockLogMapper.selectOne(new LambdaQueryWrapper<StockLog>()
                .eq(StockLog::getSkuId, skuId)
                .eq(StockLog::getBizType, "PURCHASE_IN")
                .eq(StockLog::getBizId, purchaseId)
                .orderByDesc(StockLog::getCreatedAt)
                .last("limit 1"));
        if (mine == null || mine.getCreatedAt() == null || later.getCreatedAt() == null) {
            return mine != null;
        }
        return !later.getCreatedAt().isAfter(mine.getCreatedAt());
    }

    private List<PurchaseItem> buildLines(List<PurchaseRequests.Line> requests, Long purchaseId, boolean boss, List<PurchaseItem> previous) {
        List<PurchaseItem> lines = new ArrayList<>();
        for (PurchaseRequests.Line request : requests) {
            if (request.getSkuId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择规格");
            }
            int qty = request.getQuantity() == null ? 0 : request.getQuantity();
            if (qty <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "数量须大于 0");
            }
            ProductSku sku = productSkuMapper.selectById(request.getSkuId());
            if (sku == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "商品规格不存在");
            }
            Product product = productMapper.selectById(sku.getProductId());
            PurchaseItem line = new PurchaseItem();
            line.setPurchaseId(purchaseId);
            line.setSkuId(sku.getId());
            line.setProductName(product == null ? sku.getTitle() : product.getName());
            line.setSkuName(sku.getTitle());
            line.setQuantity(qty);
            Long unit = null;
            if (boss) {
                if (request.getUnitCost() != null && request.getUnitCost() < 0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "进价不能为负");
                }
                unit = request.getUnitCost();
            } else {
                unit = previous.stream()
                        .filter(old -> sku.getId().equals(old.getSkuId()))
                        .map(PurchaseItem::getUnitCost)
                        .findFirst()
                        .orElse(null);
            }
            line.setUnitCost(unit);
            line.setLineAmount(unit == null ? null : unit * qty);
            lines.add(line);
        }
        return lines;
    }

    private void applyInvoice(PurchaseOrder order, Boolean invoiced, String type, Integer taxRate, Boolean taxIncluded) {
        boolean on = Boolean.TRUE.equals(invoiced);
        if (taxRate != null && (taxRate < 0 || taxRate > 10000)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "税率请按百分数乘 100 填写，例如 13% 填 1300");
        }
        order.setInvoiced(on ? 1 : 0);
        order.setInvoiceType(on ? blank(type) : null);
        order.setTaxRate(on ? taxRate : null);
        order.setTaxIncluded(!on || taxIncluded == null ? null : (taxIncluded ? 1 : 0));
    }

    private void writeStockLog(long skuId, String type, long bizId, int qty, Integer stockAfter, Long unitCost) {
        StockLog log = new StockLog();
        log.setSkuId(skuId);
        log.setBizType(type);
        log.setBizId(bizId);
        log.setQuantity(qty);
        log.setStockAfter(stockAfter == null ? 0 : stockAfter);
        log.setUnitCost(unitCost);
        try {
            log.setOperatorName(UserContext.require().getDisplayName());
        } catch (RuntimeException ex) {
            log.setOperatorName("系统");
        }
        log.setCreatedAt(DateTimes.now());
        stockLogMapper.insert(log);
    }

    private PurchaseVo.Detail toDetail(PurchaseOrder order, List<PurchaseItem> lines, boolean boss) {
        Supplier supplier = supplierMapper.selectById(order.getSupplierId());
        return new PurchaseVo.Detail(
                order.getId(),
                order.getPurchaseNo(),
                order.getSupplierId(),
                supplier == null ? "" : supplier.getName(),
                order.getOrderDate(),
                order.getStatus(),
                order.getInvoiced() != null && order.getInvoiced() == 1,
                order.getInvoiceType(),
                order.getTaxRate(),
                order.getTaxIncluded() == null ? null : order.getTaxIncluded() == 1,
                Jsons.readStrings(order.getAttachmentUrls()),
                order.getRemark(),
                order.getOperatorName(),
                DateTimes.format(order.getCreatedAt()),
                lines.stream().map(line -> new PurchaseVo.Line(
                        line.getId(),
                        line.getSkuId(),
                        line.getProductName(),
                        line.getSkuName(),
                        line.getQuantity() == null ? 0 : line.getQuantity(),
                        boss ? line.getUnitCost() : null,
                        boss ? line.getLineAmount() : null
                )).toList(),
                boss ? nz(order.getFreightAmount()) : null,
                boss ? nz(order.getGoodsAmount()) : null,
                boss ? nz(order.getTotalAmount()) : null
        );
    }

    private PurchaseVo.SupplierItem toSupplier(Supplier row, boolean boss, Map<Long, long[]> totals) {
        long[] sum = totals.getOrDefault(row.getId(), new long[3]);
        return new PurchaseVo.SupplierItem(
                row.getId(),
                row.getName(),
                row.getContact(),
                row.getMobile(),
                row.getBrands(),
                row.getAccountDays(),
                row.getRemark(),
                boss ? sum[0] : null,
                boss ? sum[1] : null,
                boss ? sum[0] - sum[1] - sum[2] : null
        );
    }

    private PurchaseVo.LedgerRow toLedger(SupplierLedger row) {
        return new PurchaseVo.LedgerRow(
                row.getId(),
                row.getSupplierId(),
                row.getPurchaseId(),
                row.getEntryType(),
                nz(row.getAmount()),
                row.getMethod(),
                row.getRemark(),
                row.getOperatorName(),
                DateTimes.format(row.getCreatedAt())
        );
    }

    private Map<Long, long[]> ledgerTotals(List<Long> supplierIds) {
        Map<Long, long[]> map = new HashMap<>();
        if (supplierIds.isEmpty()) {
            return map;
        }
        List<SupplierLedger> rows = supplierLedgerMapper.selectList(new LambdaQueryWrapper<SupplierLedger>()
                .in(SupplierLedger::getSupplierId, supplierIds));
        for (SupplierLedger row : rows) {
            long[] totals = map.computeIfAbsent(row.getSupplierId(), key -> new long[3]);
            long amount = nz(row.getAmount());
            if ("PAYABLE".equals(row.getEntryType())) {
                totals[0] += amount;
            } else if ("PAID".equals(row.getEntryType())) {
                totals[1] += amount;
            } else if ("VOID".equals(row.getEntryType())) {
                totals[2] += amount;
            }
        }
        return map;
    }

    private long[] sum(List<SupplierLedger> rows) {
        long[] totals = new long[3];
        for (SupplierLedger row : rows) {
            long amount = nz(row.getAmount());
            if ("PAYABLE".equals(row.getEntryType())) {
                totals[0] += amount;
            } else if ("PAID".equals(row.getEntryType())) {
                totals[1] += amount;
            } else if ("VOID".equals(row.getEntryType())) {
                totals[2] += amount;
            }
        }
        return totals;
    }

    private List<PurchaseItem> itemsOf(long purchaseId) {
        return purchaseItemMapper.selectList(new LambdaQueryWrapper<PurchaseItem>()
                .eq(PurchaseItem::getPurchaseId, purchaseId)
                .orderByAsc(PurchaseItem::getId));
    }

    private Supplier requireSupplier(long id) {
        Supplier row = supplierMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
        return row;
    }

    private PurchaseOrder requirePurchase(long id) {
        PurchaseOrder row = purchaseOrderMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采购单不存在");
        }
        return row;
    }

    private String nextPurchaseNo() {
        String day = LocalDate.now(SHANGHAI).format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "PO" + day;
        long count = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .likeRight(PurchaseOrder::getPurchaseNo, prefix));
        return prefix + String.format("%04d", count + 1);
    }

    private long nextId() {
        return idGenerator.nextId(null).longValue();
    }

    private static boolean boss() {
        return StaffRoles.isBoss(UserContext.currentOrNull());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
