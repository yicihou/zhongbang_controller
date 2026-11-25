package com.zhongbang.controller.service.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.stock.StockLogMapper;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.stock.entity.StockLog;
import com.zhongbang.controller.pojo.stock.vo.StockVo;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存余额的唯一出口。采购入库、销售出库、取消回滚、盘点调整都往 {@code stock_log} 写一条，
 * 这样「期末库存 = 期初 + 进货 − 销售 ± 盘点」在流水表里能对上。
 *
 * <p>写入约定：先改 {@code product_sku.stock}，再调 {@link #record}，因为它读的是改完之后的余额。
 * 数量正数是增加，负数是减少，和表注释一致。
 */
@Service
public class StockLogService {

    /** 期初库存：新建商品时第一次填的数量。 */
    public static final String INIT = "INIT";
    /** 采购入库。 */
    public static final String PURCHASE_IN = "PURCHASE_IN";
    /** 入库单作废，把数量减回去。 */
    public static final String PURCHASE_VOID = "PURCHASE_VOID";
    /** 销售出库：下单时扣库存。 */
    public static final String SALE_OUT = "SALE_OUT";
    /** 订单取消，把库存加回来。 */
    public static final String SALE_CANCEL = "SALE_CANCEL";
    /** 盘点调整：按实际数量把账面改平，差额记在这一笔上。 */
    public static final String ADJUST = "ADJUST";

    private final StockLogMapper stockLogMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;

    public StockLogService(
            StockLogMapper stockLogMapper,
            ProductSkuMapper productSkuMapper,
            ProductMapper productMapper
    ) {
        this.stockLogMapper = stockLogMapper;
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
    }

    /**
     * 记一笔库存变动。必须在校验和余额更新之后调用：库存不够时上层已经抛异常，这里不该再写。
     * 数量为 0 不写，避免盘点没变化也留一条空记录。
     */
    @Transactional
    public void record(long skuId, String bizType, Long bizId, int quantity, Long unitCost) {
        if (quantity == 0) {
            return;
        }
        ProductSku sku = productSkuMapper.selectById(skuId);
        int stockAfter = sku == null || sku.getStock() == null ? 0 : sku.getStock();
        StockLog log = new StockLog();
        log.setSkuId(skuId);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setQuantity(quantity);
        log.setStockAfter(stockAfter);
        log.setUnitCost(unitCost);
        log.setOperatorName(currentOperator());
        log.setCreatedAt(DateTimes.now());
        stockLogMapper.insert(log);
    }

    /**
     * 盘点调整。老板点的是「实际有多少」，系统自己算差额写流水，不让人直接改余额。
     * 差额为 0 时明确拒绝，避免以为点成功了其实什么都没变。
     */
    @Transactional
    public StockVo.AdjustResult adjust(long skuId, int actualStock, String reason) {
        StaffRoles.requireBoss("只有老板可以盘点调整库存");
        if (actualStock < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实际库存不能为负");
        }
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品规格不存在");
        }
        int before = sku.getStock() == null ? 0 : sku.getStock();
        int delta = actualStock - before;
        if (delta == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实际库存和账面一致，不用调整");
        }
        sku.setStock(actualStock);
        sku.setUpdatedAt(DateTimes.now());
        productSkuMapper.updateById(sku);
        record(skuId, ADJUST, null, delta, sku.getCostPrice());
        return new StockVo.AdjustResult(skuId, before, actualStock, delta, reason == null ? "" : reason.trim());
    }

    /** 按规格倒序查流水。skuId 为空时查全店，方便老板从前往后对账。 */
    public PageResult<StockVo.LogRow> logs(Long skuId, String bizType, String from, String to, int page, int size) {
        UserContext.require();
        int current = PageResult.normalizePage(page);
        int pageSize = PageResult.normalizeSize(size);
        LambdaQueryWrapper<StockLog> wrapper = new LambdaQueryWrapper<StockLog>()
                .orderByDesc(StockLog::getCreatedAt)
                .orderByDesc(StockLog::getId);
        if (skuId != null) {
            wrapper.eq(StockLog::getSkuId, skuId);
        }
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(StockLog::getBizType, bizType.trim());
        }
        if (from != null && !from.isBlank()) {
            wrapper.ge(StockLog::getCreatedAt, from.trim() + " 00:00:00");
        }
        if (to != null && !to.isBlank()) {
            wrapper.le(StockLog::getCreatedAt, to.trim() + " 23:59:59");
        }
        Page<StockLog> result = stockLogMapper.selectPage(new Page<>(current, pageSize), wrapper);
        Map<Long, String[]> names = namesOf(result.getRecords().stream().map(StockLog::getSkuId).distinct().toList());
        List<StockVo.LogRow> rows = new ArrayList<>();
        for (StockLog log : result.getRecords()) {
            String[] name = names.getOrDefault(log.getSkuId(), new String[]{"", "", ""});
            rows.add(new StockVo.LogRow(
                    log.getId(),
                    log.getSkuId(),
                    name[0],
                    name[1],
                    name[2],
                    log.getBizType(),
                    log.getBizId(),
                    log.getQuantity() == null ? 0 : log.getQuantity(),
                    log.getStockAfter() == null ? 0 : log.getStockAfter(),
                    log.getUnitCost(),
                    log.getOperatorName(),
                    log.getCreatedAt() == null ? null : log.getCreatedAt().toString().replace('T', ' ')
            ));
        }
        return PageResult.of(rows, result.getTotal(), current, pageSize);
    }

    private Map<Long, String[]> namesOf(List<Long> skuIds) {
        Map<Long, String[]> names = new HashMap<>();
        if (skuIds.isEmpty()) {
            return names;
        }
        List<ProductSku> skus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().in(ProductSku::getId, skuIds));
        Map<Long, Product> products = new HashMap<>();
        for (ProductSku sku : skus) {
            Product product = products.computeIfAbsent(sku.getProductId(), productMapper::selectById);
            names.put(sku.getId(), new String[]{
                    product == null ? "" : product.getName(),
                    product == null || product.getModel() == null ? "" : product.getModel(),
                    sku.getTitle() == null ? "" : sku.getTitle()
            });
        }
        return names;
    }

    /** 没有登录用户时是系统在写（演示数据、导入），流水上记「系统」。 */
    private String currentOperator() {
        try {
            return UserContext.require().getDisplayName();
        } catch (RuntimeException ex) {
            return "系统";
        }
    }
}
