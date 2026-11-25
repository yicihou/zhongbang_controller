package com.zhongbang.controller.service.cost;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.cost.CostPriceLogMapper;
import com.zhongbang.controller.mapper.cost.FinanceSettingMapper;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.pojo.cost.entity.CostPriceLog;
import com.zhongbang.controller.pojo.cost.entity.FinanceSetting;
import com.zhongbang.controller.pojo.cost.vo.CostVo;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.service.order.OrderBundle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 规格进价。老板可以改当前进价，每次修改都留历史。
 * 下单时把当时的进价抄到订单行上；当时还没有进价就标成未知，发货时如果已经有了再补一次。
 * 已经抄下来的数字不再改，避免以后调进价把旧订单的毛利算乱。
 */
@Service
public class CostPriceService {

    public static final String MOVING_AVERAGE = "MOVING_AVERAGE";
    public static final String LATEST = "LATEST";
    public static final String DEMO_REASON = "演示数据";

    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final CostPriceLogMapper costPriceLogMapper;
    private final FinanceSettingMapper financeSettingMapper;

    public CostPriceService(
            ProductSkuMapper productSkuMapper,
            ProductMapper productMapper,
            CostPriceLogMapper costPriceLogMapper,
            FinanceSettingMapper financeSettingMapper
    ) {
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
        this.costPriceLogMapper = costPriceLogMapper;
        this.financeSettingMapper = financeSettingMapper;
    }

    public FinanceSetting setting() {
        FinanceSetting row = financeSettingMapper.selectById(1L);
        if (row != null) {
            return row;
        }
        FinanceSetting created = new FinanceSetting();
        created.setId(1L);
        created.setCostMethod(MOVING_AVERAGE);
        created.setDefaultMinStock(2);
        created.setSuggestMultiplier(2);
        created.setHotTurnoverDays(30);
        created.setSlowTurnoverDays(90);
        financeSettingMapper.insert(created);
        return created;
    }

    public CostVo.Setting readSetting() {
        StaffRoles.requireBoss("只有老板可以查看进价设置");
        return toSetting(setting());
    }

    @Transactional
    public CostVo.Setting updateSetting(String costMethod, Integer defaultMinStock, Integer suggestMultiplier,
                                        Integer hotTurnoverDays, Integer slowTurnoverDays) {
        StaffRoles.requireBoss("只有老板可以修改进价设置");
        FinanceSetting row = setting();
        if (costMethod != null && !costMethod.isBlank()) {
            String method = costMethod.trim().toUpperCase();
            if (!MOVING_AVERAGE.equals(method) && !LATEST.equals(method)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "计价方式只能是移动加权平均或最近进价");
            }
            row.setCostMethod(method);
        }
        if (defaultMinStock != null) {
            if (defaultMinStock < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "默认最低库存不能为负");
            }
            row.setDefaultMinStock(defaultMinStock);
        }
        if (suggestMultiplier != null) {
            if (suggestMultiplier <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "建议采购量系数须大于 0");
            }
            row.setSuggestMultiplier(suggestMultiplier);
        }
        if (hotTurnoverDays != null && hotTurnoverDays > 0) {
            row.setHotTurnoverDays(hotTurnoverDays);
        }
        if (slowTurnoverDays != null && slowTurnoverDays > 0) {
            row.setSlowTurnoverDays(slowTurnoverDays);
        }
        financeSettingMapper.updateById(row);
        return toSetting(row);
    }

    @Transactional
    public CostVo.History setCost(long skuId, Long costPrice, String reason) {
        StaffRoles.requireBoss("只有老板可以修改进价");
        if (costPrice == null || costPrice < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写进价，单位是分，不能为负");
        }
        ProductSku sku = requireSku(skuId);
        change(sku, costPrice, operatorName(), reason);
        return history(skuId);
    }

    public CostVo.History history(long skuId) {
        StaffRoles.requireBoss("只有老板可以查看进价");
        ProductSku sku = requireSku(skuId);
        List<CostPriceLog> logs = costPriceLogMapper.selectList(new LambdaQueryWrapper<CostPriceLog>()
                .eq(CostPriceLog::getSkuId, skuId)
                .orderByDesc(CostPriceLog::getCreatedAt)
                .last("limit 50"));
        return new CostVo.History(sku.getId(), sku.getTitle(), sku.getCostPrice(), logs.stream().map(this::toLog).toList());
    }

    /**
     * 入库后按当前设置更新进价。调用前库存已经加上了 qty。
     * 旧进价未知时不把旧库存当成 0 元，直接采用本批进价。
     */
    @Transactional
    public void applyInbound(long skuId, int qty, Long unitCost, String operator, String reason) {
        if (unitCost == null || qty <= 0) {
            return;
        }
        ProductSku sku = requireSku(skuId);
        int stockNow = sku.getStock() == null ? 0 : sku.getStock();
        int oldStock = Math.max(0, stockNow - qty);
        String method = normalizeMethod(setting().getCostMethod());
        long next = LATEST.equals(method)
                ? unitCost
                : TradeMath.average(sku.getCostPrice(), oldStock, unitCost, qty);
        change(sku, next, operator, reason);
    }

    /** 作废入库时把进价退回去。库存已经减过。stockBeforeVoid 是减之前的数量。 */
    @Transactional
    public void rollbackInbound(long skuId, int qty, Long unitCost, Long costBefore, int stockBeforeReceipt,
                                String method, boolean latestReceipt, String operator, String reason) {
        ProductSku sku = requireSku(skuId);
        int stockNow = sku.getStock() == null ? 0 : sku.getStock();
        int stockBeforeVoid = stockNow + qty;
        Long next;
        if (LATEST.equals(normalizeMethod(method))) {
            next = latestReceipt ? costBefore : sku.getCostPrice();
        } else if (stockNow == stockBeforeReceipt) {
            next = costBefore;
        } else if (unitCost == null) {
            next = sku.getCostPrice();
        } else {
            next = TradeMath.rollbackAverage(sku.getCostPrice(), stockBeforeVoid, unitCost, qty, costBefore);
        }
        change(sku, next, operator, reason);
    }

    /** 下单时抄进价。已经有快照的行不动。没有进价就标未知，不写 0。 */
    public void snapshot(OrderLine line, ProductSku sku) {
        if (line.getCostKnown() != null && line.getCostKnown() == 1) {
            return;
        }
        if (sku == null || sku.getCostPrice() == null) {
            line.setCostKnown(0);
            line.setCostPrice(null);
            line.setCostAmount(null);
            return;
        }
        int qty = line.getQuantity() == null ? 0 : line.getQuantity();
        line.setCostKnown(1);
        line.setCostPrice(sku.getCostPrice());
        line.setCostAmount(sku.getCostPrice() * qty);
    }

    /** 发货时，只给还是未知成本的行补快照。 */
    public void fillOnShip(OrderBundle bundle) {
        for (OrderLine line : bundle.getItems()) {
            if (line.getCostKnown() != null && line.getCostKnown() == 1) {
                continue;
            }
            snapshot(line, productSkuMapper.selectById(line.getSkuId()));
        }
    }

    /** 演示数据清掉之后，按还留着的历史把当前进价拨回去。没有历史就清空。 */
    @Transactional
    public void rebuildFromRemainingLogs() {
        List<ProductSku> skus = productSkuMapper.selectList(new LambdaQueryWrapper<>());
        for (ProductSku sku : skus) {
            CostPriceLog latest = costPriceLogMapper.selectOne(new LambdaQueryWrapper<CostPriceLog>()
                    .eq(CostPriceLog::getSkuId, sku.getId())
                    .orderByDesc(CostPriceLog::getCreatedAt)
                    .orderByDesc(CostPriceLog::getId)
                    .last("limit 1"));
            Long cost = latest == null ? null : latest.getAfterPrice();
            productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                    .eq(ProductSku::getId, sku.getId())
                    .set(ProductSku::getCostPrice, cost));
        }
    }

    public void change(ProductSku sku, Long next, String operator, String reason) {
        Long before = sku.getCostPrice();
        if (before == null && next == null) {
            return;
        }
        if (before != null && before.equals(next)) {
            return;
        }
        productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                .eq(ProductSku::getId, sku.getId())
                .set(ProductSku::getCostPrice, next)
                .set(ProductSku::getUpdatedAt, DateTimes.now()));
        sku.setCostPrice(next);
        CostPriceLog log = new CostPriceLog();
        log.setSkuId(sku.getId());
        log.setBeforePrice(before);
        log.setAfterPrice(next);
        log.setOperatorName(operator == null || operator.isBlank() ? "系统" : operator);
        log.setReason(blank(reason));
        log.setCreatedAt(DateTimes.now());
        costPriceLogMapper.insert(log);
    }

    public int minStockOf(ProductSku sku) {
        if (sku.getMinStock() != null) {
            return sku.getMinStock();
        }
        Integer fallback = setting().getDefaultMinStock();
        return fallback == null ? 0 : fallback;
    }

    public String normalizeMethod(String method) {
        return LATEST.equalsIgnoreCase(method) ? LATEST : MOVING_AVERAGE;
    }

    private ProductSku requireSku(long skuId) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品规格不存在");
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return sku;
    }

    private CostVo.Setting toSetting(FinanceSetting row) {
        return new CostVo.Setting(
                normalizeMethod(row.getCostMethod()),
                row.getDefaultMinStock() == null ? 2 : row.getDefaultMinStock(),
                row.getSuggestMultiplier() == null ? 2 : row.getSuggestMultiplier(),
                row.getHotTurnoverDays() == null ? 30 : row.getHotTurnoverDays(),
                row.getSlowTurnoverDays() == null ? 90 : row.getSlowTurnoverDays()
        );
    }

    private CostVo.Log toLog(CostPriceLog row) {
        return new CostVo.Log(
                row.getId(),
                row.getSkuId(),
                row.getBeforePrice(),
                row.getAfterPrice(),
                row.getOperatorName(),
                row.getReason(),
                DateTimes.format(row.getCreatedAt())
        );
    }

    private static String operatorName() {
        return UserContext.require().getDisplayName();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
