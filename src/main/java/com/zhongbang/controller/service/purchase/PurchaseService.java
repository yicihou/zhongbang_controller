package com.zhongbang.controller.service.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.purchase.PurchaseCorrectionMapper;
import com.zhongbang.controller.mapper.purchase.PurchaseItemMapper;
import com.zhongbang.controller.mapper.purchase.PurchaseOrderMapper;
import com.zhongbang.controller.mapper.purchase.SupplierLedgerMapper;
import com.zhongbang.controller.mapper.purchase.SupplierMapper;
import com.zhongbang.controller.mapper.stock.StockLogMapper;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.purchase.dto.PurchaseRequests;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseCorrection;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;

/**
 * 供应商和采购入库。
 * 确认入库后更新库存、成本和供应商应付，已入库单据的更正及作废由老板操作。
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
    private final PurchaseCorrectionMapper correctionMapper;
    private final ObjectMapper objectMapper;

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
            IdentifierGenerator idGenerator,
            PurchaseCorrectionMapper correctionMapper,
            ObjectMapper objectMapper
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
        this.correctionMapper = correctionMapper;
        this.objectMapper = objectMapper;
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
                .notIn(PurchaseOrder::getStatus, "VOID", "MERGED"));
        if (used > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "还有未作废的采购单，不能删除供应商");
        }
        if (productMapper.exists(new LambdaQueryWrapper<Product>().eq(Product::getPurchaseSupplierId, id))) {
            throw new BusinessException(ErrorCode.CONFLICT, "还有商品指定了此供应商，请先修改商品资料");
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
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>()
                .ne(PurchaseOrder::getStatus, "MERGED").orderByDesc(PurchaseOrder::getId);
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

    public PageResult<PurchaseVo.BatchRow> listBatches(String keyword, int page, int size) {
        UserContext.require();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        QueryWrapper<PurchaseOrder> query = new QueryWrapper<PurchaseOrder>()
                .and(root -> root.isNull("batch_id").or().apply("id = batch_id"))
                .orderByDesc("id");
        if (blank(keyword) != null) {
            query.like("purchase_no", keyword.trim());
        }
        Page<PurchaseOrder> roots = purchaseOrderMapper.selectPage(new Page<>(p, s), query);
        List<Long> batchIds = roots.getRecords().stream().map(PurchaseOrder::getId).toList();
        Map<Long, List<PurchaseOrder>> grouped = batchIds.isEmpty() ? Map.of()
                : purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                                .in(PurchaseOrder::getBatchId, batchIds).orderByAsc(PurchaseOrder::getId))
                        .stream().collect(Collectors.groupingBy(PurchaseOrder::getBatchId));
        List<PurchaseVo.BatchRow> rows = roots.getRecords().stream()
                .map(root -> summarize(root, grouped.getOrDefault(root.getId(), List.of(root)), boss()))
                .toList();
        return PageResult.of(rows, roots.getTotal(), p, s);
    }

    @Transactional(readOnly = true)
    public PurchaseVo.Batch getBatch(long id) {
        UserContext.require();
        PurchaseOrder root = requirePurchase(id);
        if (root.getBatchId() != null && !root.getBatchId().equals(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "进货记录不存在");
        }
        return toBatch(root);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseVo.Batch createBatch(PurchaseRequests.BatchCreate request) {
        UserContext.require();
        String token = clientToken(request);
        PurchaseOrder existing = findBatchByToken(token);
        if (existing != null) {
            return toBatch(existing);
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择进货商品");
        }
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>());
        Map<Long, Supplier> suppliersById = suppliers.stream().collect(Collectors.toMap(Supplier::getId, row -> row));
        Map<Long, Map<Long, PurchaseRequests.Line>> groups = new LinkedHashMap<>();
        for (PurchaseRequests.Selection selected : request.lines()) {
            if (selected == null || selected.skuId() == null || selected.quantity() == null || selected.quantity() <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择商品规格并填写有效数量");
            }
            ProductSku sku = productSkuMapper.selectById(selected.skuId());
            Product product = sku == null ? null : productMapper.selectById(sku.getProductId());
            if (product == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "商品规格不存在");
            }
            Long supplierId = selected.supplierId();
            if (supplierId == null) {
                supplierId = defaultSupplier(product, suppliers);
            }
            if (supplierId == null || !suppliersById.containsKey(supplierId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择「" + product.getName() + "」的供应商");
            }
            Map<Long, PurchaseRequests.Line> lines = groups.computeIfAbsent(supplierId, key -> new LinkedHashMap<>());
            PurchaseRequests.Line line = lines.computeIfAbsent(sku.getId(), id -> newLine(id, 0, null));
            line.setQuantity(addQuantities(line.getQuantity(), selected.quantity()));
        }
        PurchaseOrder root = newDraft();
        root.setBatchId(root.getId());
        root.setClientToken(token);
        boolean first = true;
        for (var group : groups.entrySet()) {
            PurchaseOrder child = first ? root : newDraft();
            first = false;
            child.setBatchId(root.getId());
            PurchaseRequests.PurchaseSave draft = new PurchaseRequests.PurchaseSave();
            draft.setSupplierId(group.getKey());
            draft.setLines(new ArrayList<>(group.getValue().values()));
            storeDraft(child, draft, List.of(), true);
        }
        return toBatch(root);
    }

    @Transactional(readOnly = true)
    public PurchaseVo.Batch retryBatch(PurchaseRequests.BatchCreate request) {
        PurchaseOrder root = findBatchByToken(clientToken(request));
        return root == null ? null : toBatch(root);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseVo.Detail savePurchase(Long id, PurchaseRequests.PurchaseSave request) {
        UserContext.require();
        requirePurchaseFields(request);
        requireSupplier(request.getSupplierId());
        if (id == null) {
            PurchaseOrder order = newDraft();
            storeDraft(order, request, List.of(), true);
            return toDetail(order, itemsOf(order.getId()), boss());
        }
        PurchaseOrder order = lockPurchase(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "只有草稿可以修改");
        }
        checkRevision(order, request.getRevision());
        List<PurchaseItem> previous = itemsOf(id);
        if (order.getBatchId() != null && !order.getSupplierId().equals(request.getSupplierId())) {
            PurchaseOrder target = batchOrders(order).stream()
                    .filter(row -> !row.getId().equals(id) && row.getSupplierId().equals(request.getSupplierId()))
                    .filter(row -> !"VOID".equals(row.getStatus()) && !"MERGED".equals(row.getStatus()))
                    .findFirst().orElse(null);
            if (target != null) {
                return mergeDraft(order, target, request, previous);
            }
        }
        storeDraft(order, request, previous, false);
        return toDetail(order, itemsOf(id), boss());
    }

    private PurchaseVo.Detail mergeDraft(PurchaseOrder source, PurchaseOrder target, PurchaseRequests.PurchaseSave request,
                                         List<PurchaseItem> previous) {
        if (!"DRAFT".equals(target.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该供应商的子单已入库");
        }
        boolean sourceInvoiced = Boolean.TRUE.equals(request.getInvoiced());
        if (sourceInvoiced != (target.getInvoiced() == 1) || sourceInvoiced
                && (!Objects.equals(blank(request.getInvoiceType()), target.getInvoiceType())
                || !Objects.equals(request.getTaxRate(), target.getTaxRate())
                || !Objects.equals(request.getTaxIncluded(), target.getTaxIncluded() == null ? null : target.getTaxIncluded() == 1))) {
            throw new BusinessException(ErrorCode.CONFLICT, "两张草稿的开票信息不同，请先调整开票信息");
        }
        List<PurchaseItem> incoming = buildLines(request.getLines(), target.getId(), boss(), previous);
        Map<Long, PurchaseItem> merged = new LinkedHashMap<>();
        for (PurchaseItem item : itemsOf(target.getId())) {
            mergeLine(merged, item);
        }
        for (PurchaseItem item : incoming) {
            mergeLine(merged, item);
        }
        long freight = addAmounts(requestFreight(source, request), nz(target.getFreightAmount()));
        List<String> attachments = new ArrayList<>(Jsons.readStrings(target.getAttachmentUrls()));
        attachments.addAll(request.getAttachmentUrls() == null ? List.of() : request.getAttachmentUrls());
        target.setAttachmentUrls(Jsons.writeStrings(attachments.stream().distinct().toList()));
        String memo = blank(request.getRemark());
        if (memo != null) {
            target.setRemark(target.getRemark() == null ? memo : target.getRemark() + "\n" + memo);
        }
        target.setFreightAmount(freight);
        touch(target);
        persistPurchase(target, new ArrayList<>(merged.values()), false);
        source.setStatus("MERGED");
        touch(source);
        purchaseOrderMapper.updateById(source);
        return toDetail(target, itemsOf(target.getId()), boss());
    }

    private void mergeLine(Map<Long, PurchaseItem> lines, PurchaseItem line) {
        PurchaseItem old = lines.putIfAbsent(line.getSkuId(), line);
        if (old == null) {
            return;
        }
        if (!Objects.equals(old.getUnitCost(), line.getUnitCost())) {
            throw new BusinessException(ErrorCode.CONFLICT, "相同规格的进价不同，请先核对进价");
        }
        old.setQuantity(addQuantities(old.getQuantity(), line.getQuantity()));
        old.setLineAmount(lineAmount(old.getUnitCost(), old.getQuantity()));
    }

    private PurchaseOrder newDraft() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(nextId());
        order.setPurchaseNo(purchaseNo(order.getId()));
        order.setStatus("DRAFT");
        order.setSource("MANUAL");
        order.setCreatedAt(DateTimes.now());
        order.setRevision(0);
        order.setFreightAmount(0L);
        return order;
    }

    private void storeDraft(PurchaseOrder order, PurchaseRequests.PurchaseSave request,
                            List<PurchaseItem> previous, boolean creating) {
        applyPurchase(order, request);
        List<PurchaseItem> lines = buildLines(request.getLines(), order.getId(), boss(), previous);
        persistPurchase(order, lines, creating);
    }

    private void applyPurchase(PurchaseOrder order, PurchaseRequests.PurchaseSave request) {
        order.setSupplierId(request.getSupplierId());
        order.setOrderDate(blank(request.getOrderDate()) == null ? LocalDate.now(SHANGHAI).toString() : request.getOrderDate().trim());
        applyInvoice(order, request.getInvoiced(), request.getInvoiceType(), request.getTaxRate(), request.getTaxIncluded());
        order.setAttachmentUrls(Jsons.writeStrings(request.getAttachmentUrls()));
        order.setRemark(blank(request.getRemark()));
        order.setFreightAmount(requestFreight(order, request));
        touch(order);
    }

    private long requestFreight(PurchaseOrder order, PurchaseRequests.PurchaseSave request) {
        if (!boss()) {
            return nz(order.getFreightAmount());
        }
        long freight = nz(request.getFreightAmount());
        if (freight < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "运费不能为负");
        }
        return freight;
    }

    private void persistPurchase(PurchaseOrder order, List<PurchaseItem> lines, boolean creating) {
        long goods = lines.stream().mapToLong(line -> nz(line.getLineAmount())).reduce(0L, this::addAmounts);
        order.setGoodsAmount(goods);
        order.setTotalAmount(addAmounts(goods, nz(order.getFreightAmount())));
        if (creating) {
            purchaseOrderMapper.insert(order);
        } else {
            purchaseOrderMapper.updateById(order);
            purchaseItemMapper.delete(new LambdaQueryWrapper<PurchaseItem>().eq(PurchaseItem::getPurchaseId, order.getId()));
        }
        for (PurchaseItem line : lines) {
            line.setId(null);
            line.setPurchaseId(order.getId());
            purchaseItemMapper.insert(line);
        }
    }

    /** 给导入和演示数据直接落下已经算好的采购单。applyStock 为真时才改库存和进价。 */
    @Transactional
    public PurchaseOrder insertReady(PurchaseOrder order, List<PurchaseItem> lines, boolean applyStock, String reason) {
        if (order.getId() == null) {
            order.setId(nextId());
        }
        if (order.getPurchaseNo() == null || order.getPurchaseNo().isBlank()) {
            order.setPurchaseNo(purchaseNo(order.getId()));
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseVo.Detail receive(long id, Integer expectedRevision) {
        UserContext.require();
        PurchaseOrder order = lockPurchase(id);
        if ("RECEIVED".equals(order.getStatus())) {
            return toDetail(order, itemsOf(id), boss());
        }
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "只有草稿可以入库");
        }
        checkRevision(order, expectedRevision);
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseVo.Detail voidPurchase(long id, Integer expectedRevision) {
        UserContext.require();
        PurchaseOrder order = lockPurchase(id);
        if ("VOID".equals(order.getStatus()) || "MERGED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "采购单已经作废");
        }
        checkRevision(order, expectedRevision);
        List<PurchaseItem> lines = itemsOf(id);
        if ("RECEIVED".equals(order.getStatus())) {
            StaffRoles.requireBoss("只有老板可以作废已入库的采购单");
            String reason = "DEMO".equals(order.getSource()) ? CostPriceService.DEMO_REASON : null;
            List<PurchaseItem> reversed = new ArrayList<>(lines);
            java.util.Collections.reverse(reversed);
            if (tracksInventory(order)) {
                lockSkus(lines.stream().map(PurchaseItem::getSkuId).toList());
            } else {
                reversed.clear();
            }
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
        touch(order);
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
        lockSkus(lines.stream().map(PurchaseItem::getSkuId).toList());
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
        order.setRevision(revision(order) + 1);
        order.setCostMethod(method);
        order.setUpdatedAt(DateTimes.now());
        order.setOperatorName(operator);
        purchaseOrderMapper.updateById(order);
        writePayable(order, reason);
    }

    private void writePayable(PurchaseOrder order, String reason) {
        long total = nz(order.getTotalAmount());
        if (total > 0 && !supplierLedgerMapper.exists(new LambdaQueryWrapper<SupplierLedger>()
                .eq(SupplierLedger::getPurchaseId, order.getId()).eq(SupplierLedger::getEntryType, "PAYABLE"))) {
            appendPayable(order, total, "PAYABLE", reason);
        }
    }

    private void appendPayable(PurchaseOrder order, long delta, String entryType, String reason) {
        if (delta == 0) {
            return;
        }
        SupplierLedger ledger = new SupplierLedger();
        ledger.setSupplierId(order.getSupplierId());
        ledger.setPurchaseId(order.getId());
        ledger.setEntryType(entryType);
        ledger.setAmount(Math.abs(delta));
        ledger.setRemark(reason);
        ledger.setOperatorName(order.getOperatorName());
        ledger.setCreatedAt(DateTimes.now());
        supplierLedgerMapper.insert(ledger);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseVo.Detail correct(long id, PurchaseRequests.Correction request) {
        StaffRoles.requireBoss("只有老板可以更正已入库的采购单");
        String reason = request == null ? null : blank(request.reason());
        if (reason == null || reason.length() > 240 || request.revision() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写更正原因（最多 240 字）并确认当前版本");
        }
        PurchaseOrder order = lockPurchase(id);
        if (!"RECEIVED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "只有已入库的采购单可以更正");
        }
        checkRevision(order, request.revision());
        PurchaseRequests.PurchaseSave values = request.purchase();
        requirePurchaseFields(values);
        if (!order.getSupplierId().equals(values.getSupplierId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已入库的采购单须保留原供应商");
        }

        List<PurchaseItem> original = itemsOf(id);
        List<PurchaseItem> next = buildLines(values.getLines(), id, true, original);
        Map<Long, PurchaseItem> nextBySku = new LinkedHashMap<>();
        for (PurchaseItem line : next) {
            if (line.getUnitCost() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写更正后的每行进价");
            }
            if (nextBySku.putIfAbsent(line.getSkuId(), line) != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请合并重复的商品规格");
            }
        }

        long oldAmount = nz(order.getTotalAmount());
        PurchaseVo.Detail before = toDetail(order, original, true);
        if (tracksInventory(order)) {
            applyStockCorrection(order, original, nextBySku, reason);
        }
        applyPurchase(order, values);
        persistPurchase(order, next, false);
        long delta = nz(order.getTotalAmount()) - oldAmount;
        appendPayable(order, delta, delta > 0 ? "CORRECT_ADD" : "CORRECT_SUB", "采购更正：" + reason);
        PurchaseVo.Detail after = toDetail(order, next, true);
        var operator = UserContext.require();
        correctionMapper.insert(new PurchaseCorrection(nextId(), id, reason, operator.getUserId(),
                operator.getDisplayName(), snapshot(before), snapshot(after), DateTimes.now()));
        return after;
    }

    private void applyStockCorrection(PurchaseOrder order, List<PurchaseItem> original,
                                      Map<Long, PurchaseItem> next, String reason) {
        Map<Long, ReceiptPosition> old = new TreeMap<>();
        for (PurchaseItem item : original) {
            old.compute(item.getSkuId(), (id, position) -> position == null
                    ? new ReceiptPosition(item, item.getQuantity(), nz(item.getLineAmount()))
                    : new ReceiptPosition(position.first(), addQuantities(position.quantity(), item.getQuantity()),
                            addAmounts(position.value(), nz(item.getLineAmount()))));
        }
        List<Long> skuIds = java.util.stream.Stream.concat(old.keySet().stream(), next.keySet().stream())
                .distinct().sorted().toList();
        Map<Long, ProductSku> skus = lockSkus(skuIds);
        for (long skuId : skuIds) {
            ProductSku sku = skus.get(skuId);
            ReceiptPosition before = old.get(skuId);
            PurchaseItem after = next.get(skuId);
            int oldQuantity = before == null ? 0 : before.quantity();
            long oldValue = before == null ? 0L : before.value();
            int newQuantity = after == null ? 0 : after.getQuantity();
            long newValue = after == null ? 0L : nz(after.getLineAmount());
            int delta = newQuantity - oldQuantity;
            int stock = sku.getStock() == null ? 0 : sku.getStock();
            int stockAfter = addQuantities(stock, delta);
            if (stockAfter < 0 || delta < 0 && productSkuMapper.deductStock(skuId, -delta) != 1) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT, "当前库存不足，无法更正「" + sku.getTitle() + "」");
            }
            if (delta > 0) {
                productSkuMapper.restoreStock(skuId, delta);
            }
            if (after != null) {
                after.setBeforeStock(before == null ? stock : before.first().getBeforeStock());
                after.setBeforeCost(before == null ? sku.getCostPrice() : before.first().getBeforeCost());
            }
            if (delta != 0) {
                writeStockLog(skuId, before == null ? "PURCHASE_CORRECT_IN" : "PURCHASE_CORRECT",
                        order.getId(), delta, stockAfter, after == null ? before.first().getUnitCost() : after.getUnitCost());
                stockAlertService.evaluate(skuId);
            }
            if (delta != 0 || oldValue != newValue || before != null && before.first().getUnitCost() == null) {
                Long cost = correctedCost(order, sku, stock, stockAfter, before, after, newValue - oldValue);
                costPriceService.change(sku, cost, UserContext.require().getDisplayName(), "采购更正：" + reason);
            }
        }
    }

    private record ReceiptPosition(PurchaseItem first, int quantity, long value) {
    }

    private Long correctedCost(PurchaseOrder order, ProductSku sku, int stock, int stockAfter,
                               ReceiptPosition before, PurchaseItem after, long valueDelta) {
        Long current = sku.getCostPrice();
        if (CostPriceService.LATEST.equals(costPriceService.normalizeMethod(order.getCostMethod()))) {
            if (before != null && !isLatestInbound(sku.getId(), order.getId())) {
                return current;
            }
            return after == null ? before.first().getBeforeCost() : after.getUnitCost();
        }
        if (stockAfter <= 0) {
            return after == null ? before.first().getBeforeCost() : after.getUnitCost();
        }
        if (current == null) {
            return after == null ? null : after.getUnitCost();
        }
        if (before != null && before.first().getBeforeStock() != null
                && (long) before.first().getBeforeStock() + before.quantity() == stock
                && isLatestInbound(sku.getId(), order.getId())) {
            Long baseline = before.first().getBeforeCost();
            if (after == null) {
                return baseline;
            }
            if (baseline == null) {
                return after.getUnitCost();
            }
            return averageAfterCorrection(baseline, before.first().getBeforeStock(),
                    nz(after.getLineAmount()), stockAfter, baseline);
        }
        Long fallback = before == null ? current : before.first().getBeforeCost();
        return averageAfterCorrection(current, stock, valueDelta, stockAfter, fallback);
    }

    private Long averageAfterCorrection(long unitCost, int quantity, long amount, int stockAfter, Long fallback) {
        BigDecimal value = BigDecimal.valueOf(unitCost).multiply(BigDecimal.valueOf(quantity)).add(BigDecimal.valueOf(amount));
        if (value.signum() < 0) {
            return fallback;
        }
        try {
            return value.divide(BigDecimal.valueOf(stockAfter), 0, RoundingMode.DOWN).longValueExact();
        } catch (ArithmeticException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采购金额超出范围");
        }
    }

    private boolean tracksInventory(PurchaseOrder order) {
        return !"IMPORT".equals(order.getSource()) || stockLogMapper.exists(new LambdaQueryWrapper<StockLog>()
                .eq(StockLog::getBizId, order.getId()).eq(StockLog::getBizType, "PURCHASE_IN"));
    }

    private boolean isLatestInbound(long skuId, long purchaseId) {
        StockLog latest = stockLogMapper.selectOne(new LambdaQueryWrapper<StockLog>()
                .eq(StockLog::getSkuId, skuId)
                .in(StockLog::getBizType, "PURCHASE_IN", "PURCHASE_CORRECT_IN")
                .exists("""
                        SELECT 1 FROM purchase_order p JOIN purchase_item i ON i.purchase_id = p.id
                        WHERE p.id = stock_log.biz_id AND p.status = 'RECEIVED' AND i.sku_id = stock_log.sku_id
                        """)
                .orderByDesc(StockLog::getCreatedAt).orderByDesc(StockLog::getId).last("LIMIT 1"));
        return latest == null || latest.getBizId().equals(purchaseId);
    }

    private String snapshot(PurchaseVo.Detail detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize purchase details", ex);
        }
    }

    private PurchaseVo.Detail readSnapshot(String detail) {
        try {
            return objectMapper.readValue(detail, PurchaseVo.Detail.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to read purchase details", ex);
        }
    }

    private List<PurchaseVo.Correction> correctionHistory(long id) {
        return correctionMapper.selectList(new QueryWrapper<PurchaseCorrection>().eq("purchase_id", id).orderByDesc("id"))
                .stream().map(row -> new PurchaseVo.Correction(row.id(), row.reason(), row.operatorName(),
                        DateTimes.format(row.createdAt()), readSnapshot(row.beforeJson()), readSnapshot(row.afterJson())))
                .toList();
    }

    private List<PurchaseItem> buildLines(List<PurchaseRequests.Line> requests, Long purchaseId, boolean boss, List<PurchaseItem> previous) {
        List<PurchaseItem> lines = new ArrayList<>();
        for (PurchaseRequests.Line request : requests) {
            if (request == null || request.getSkuId() == null) {
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
                        .findFirst()
                        .map(PurchaseItem::getUnitCost)
                        .orElse(null);
            }
            line.setUnitCost(unit);
            line.setLineAmount(lineAmount(unit, qty));
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

    private PurchaseVo.Batch toBatch(PurchaseOrder root) {
        boolean boss = boss();
        List<PurchaseOrder> orders = batchOrders(root);
        List<PurchaseVo.SupplierOrder> details = orders.stream()
                .filter(order -> !"MERGED".equals(order.getStatus()))
                .map(order -> {
                    Supplier supplier = supplierMapper.selectById(order.getSupplierId());
                    return new PurchaseVo.SupplierOrder(toDetail(order, itemsOf(order.getId()), boss),
                            supplier == null ? null : supplier.getContact(), supplier == null ? null : supplier.getMobile(),
                            revision(order), boss ? correctionHistory(order.getId()) : List.of());
                }).toList();
        return new PurchaseVo.Batch(summarize(root, orders, boss), details);
    }

    private List<PurchaseOrder> batchOrders(PurchaseOrder order) {
        return order.getBatchId() == null ? List.of(order)
                : purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getBatchId, order.getBatchId()).orderByAsc(PurchaseOrder::getId));
    }

    private PurchaseVo.BatchRow summarize(PurchaseOrder root, List<PurchaseOrder> orders, boolean boss) {
        List<PurchaseOrder> visible = orders.stream().filter(order -> !"MERGED".equals(order.getStatus())).toList();
        List<PurchaseOrder> active = visible.stream().filter(order -> !"VOID".equals(order.getStatus())).toList();
        long received = active.stream().filter(order -> "RECEIVED".equals(order.getStatus())).count();
        String status;
        if (active.isEmpty()) {
            status = "VOID";
        } else if (received == 0) {
            status = "DRAFT";
        } else {
            status = received == active.size() ? "RECEIVED" : "PARTIAL";
        }
        List<Long> supplierIds = (active.isEmpty() ? visible : active).stream()
                .map(PurchaseOrder::getSupplierId).distinct().toList();
        List<String> names = supplierIds.isEmpty() ? List.of() : supplierMapper.selectBatchIds(supplierIds)
                .stream().map(Supplier::getName).sorted().toList();
        Long total = boss ? active.stream().mapToLong(order -> nz(order.getTotalAmount())).sum() : null;
        String date = root.getBatchId() == null ? root.getOrderDate() : root.getCreatedAt().toLocalDate().toString();
        return new PurchaseVo.BatchRow(root.getId(), root.getPurchaseNo(), date, status, names, total);
    }

    private Long defaultSupplier(Product product, List<Supplier> suppliers) {
        if (product.getPurchaseSupplierId() != null) {
            return product.getPurchaseSupplierId();
        }
        String brand = blank(product.getBrand());
        if (brand == null) {
            return null;
        }
        List<Supplier> named = suppliers.stream().filter(row -> brand.equalsIgnoreCase(row.getName().trim())).toList();
        if (!named.isEmpty()) {
            return named.size() == 1 ? named.getFirst().getId() : null;
        }
        List<Supplier> carrying = suppliers.stream().filter(row -> row.getBrands() != null
                && java.util.Arrays.stream(row.getBrands().split("[,，、;；]"))
                        .anyMatch(item -> brand.equalsIgnoreCase(item.trim()))).toList();
        return carrying.size() == 1 ? carrying.getFirst().getId() : null;
    }

    private String clientToken(PurchaseRequests.BatchCreate request) {
        if (request == null || request.requestId() == null || !request.requestId().matches("[a-fA-F0-9-]{16,40}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采购请求无效，请重新发起");
        }
        return UserContext.require().getUserId() + ":" + request.requestId().toLowerCase(Locale.ROOT);
    }

    private PurchaseOrder findBatchByToken(String token) {
        return purchaseOrderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getClientToken, token));
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
            addLedgerTotals(totals, row);
        }
        return map;
    }

    private long[] sum(List<SupplierLedger> rows) {
        long[] totals = new long[3];
        for (SupplierLedger row : rows) {
            addLedgerTotals(totals, row);
        }
        return totals;
    }

    private void addLedgerTotals(long[] totals, SupplierLedger row) {
        long amount = nz(row.getAmount());
        switch (row.getEntryType()) {
            case "PAYABLE", "CORRECT_ADD" -> totals[0] += amount;
            case "CORRECT_SUB" -> totals[0] -= amount;
            case "PAID" -> totals[1] += amount;
            case "VOID" -> totals[2] += amount;
            default -> { }
        }
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

    private PurchaseOrder lockPurchase(long id) {
        PurchaseOrder order = requirePurchase(id);
        if (order.getBatchId() != null && !order.getBatchId().equals(id)) {
            selectPurchaseForUpdate(order.getBatchId());
        }
        return selectPurchaseForUpdate(id);
    }

    private PurchaseOrder selectPurchaseForUpdate(long id) {
        PurchaseOrder row = purchaseOrderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, id).last("FOR UPDATE"));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采购单不存在");
        }
        return row;
    }

    private Map<Long, ProductSku> lockSkus(List<Long> skuIds) {
        List<Long> ids = skuIds.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        Map<Long, ProductSku> skus = new LinkedHashMap<>();
        for (Long id : ids) {
            ProductSku sku = productSkuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getId, id).last("FOR UPDATE"));
            if (sku == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "商品规格不存在");
            }
            skus.put(id, sku);
        }
        return skus;
    }

    private static int revision(PurchaseOrder order) {
        return order.getRevision() == null ? 0 : order.getRevision();
    }

    private void checkRevision(PurchaseOrder order, Integer expected) {
        if (expected != null && expected != revision(order)) {
            throw new BusinessException(ErrorCode.CONFLICT, "采购记录已更新，请重新打开后编辑");
        }
    }

    private void touch(PurchaseOrder order) {
        order.setRevision(revision(order) + 1);
        order.setOperatorName(UserContext.require().getDisplayName());
        order.setUpdatedAt(DateTimes.now());
    }

    private void requirePurchaseFields(PurchaseRequests.PurchaseSave request) {
        if (request == null || request.getSupplierId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择供应商");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少填写一行明细");
        }
    }

    private PurchaseRequests.Line newLine(long skuId, int quantity, Long cost) {
        PurchaseRequests.Line line = new PurchaseRequests.Line();
        line.setSkuId(skuId);
        line.setQuantity(quantity);
        line.setUnitCost(cost);
        return line;
    }

    private int addQuantities(int first, int second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "商品数量超出范围");
        }
    }

    private Long lineAmount(Long unitCost, int quantity) {
        try {
            return unitCost == null ? null : Math.multiplyExact(unitCost, quantity);
        } catch (ArithmeticException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采购金额超出范围");
        }
    }

    private long addAmounts(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采购金额超出范围");
        }
    }

    private String purchaseNo(long id) {
        return "PO" + LocalDate.now(SHANGHAI).format(DateTimeFormatter.BASIC_ISO_DATE) + id;
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
