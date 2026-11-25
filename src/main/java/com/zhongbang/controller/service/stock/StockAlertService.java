package com.zhongbang.controller.service.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.purchase.PurchaseItemMapper;
import com.zhongbang.controller.mapper.purchase.PurchaseOrderMapper;
import com.zhongbang.controller.mapper.stock.StockAlertStateMapper;
import com.zhongbang.controller.pojo.cost.entity.FinanceSetting;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseItem;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseOrder;
import com.zhongbang.controller.pojo.stock.entity.StockAlertState;
import com.zhongbang.controller.pojo.stock.vo.StockVo;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.service.cost.CostPriceService;
import com.zhongbang.controller.service.cost.TradeMath;
import com.zhongbang.controller.service.notice.NoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存低于最低库存时通知老板。同一个规格在恢复之前只提醒一次。
 * 建议采购量 = 最低库存 × 系数 − 现有库存 − 草稿采购单里的数量。
 */
@Service
public class StockAlertService {

    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final StockAlertStateMapper stockAlertStateMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseItemMapper purchaseItemMapper;
    private final CostPriceService costPriceService;
    private final NoticeService noticeService;

    public StockAlertService(
            ProductSkuMapper productSkuMapper,
            ProductMapper productMapper,
            StockAlertStateMapper stockAlertStateMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            PurchaseItemMapper purchaseItemMapper,
            CostPriceService costPriceService,
            NoticeService noticeService
    ) {
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
        this.stockAlertStateMapper = stockAlertStateMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseItemMapper = purchaseItemMapper;
        this.costPriceService = costPriceService;
        this.noticeService = noticeService;
    }

    public StockVo.Page list() {
        UserContext.require();
        FinanceSetting setting = costPriceService.setting();
        Map<Long, Integer> transit = inTransit();
        List<StockVo.Row> rows = new ArrayList<>();
        for (ProductSku sku : productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().orderByAsc(ProductSku::getId))) {
            evaluate(sku.getId());
            Product product = productMapper.selectById(sku.getProductId());
            if (product == null) {
                continue;
            }
            ProductSku fresh = productSkuMapper.selectById(sku.getId());
            int stock = fresh.getStock() == null ? 0 : fresh.getStock();
            int min = costPriceService.minStockOf(fresh);
            int onWay = transit.getOrDefault(fresh.getId(), 0);
            int suggest = TradeMath.suggest(min, setting.getSuggestMultiplier() == null ? 2 : setting.getSuggestMultiplier(), stock, onWay);
            StockAlertState state = stockAlertStateMapper.selectById(fresh.getId());
            boolean low = stock < min;
            if (!low && suggest == 0) {
                continue;
            }
            rows.add(new StockVo.Row(
                    fresh.getId(),
                    product.getId(),
                    product.getName(),
                    product.getModel(),
                    fresh.getTitle(),
                    stock,
                    min,
                    fresh.getMinStock() == null,
                    onWay,
                    suggest,
                    state != null && state.getAlerting() != null && state.getAlerting() == 1
            ));
        }
        return new StockVo.Page(
                setting.getDefaultMinStock() == null ? 2 : setting.getDefaultMinStock(),
                setting.getSuggestMultiplier() == null ? 2 : setting.getSuggestMultiplier(),
                rows
        );
    }

    @Transactional
    public void updateMinStock(long skuId, Integer minStock) {
        UserContext.require();
        if (minStock != null && minStock < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "最低库存不能为负");
        }
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品规格不存在");
        }
        productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                .eq(ProductSku::getId, skuId)
                .set(ProductSku::getMinStock, minStock)
                .set(ProductSku::getUpdatedAt, DateTimes.now()));
        evaluate(skuId);
    }

    /** 库存或最低库存变化之后调用。已经在预警中的规格不会再推一条。 */
    @Transactional
    public void evaluate(long skuId) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            return;
        }
        int stock = sku.getStock() == null ? 0 : sku.getStock();
        int min = costPriceService.minStockOf(sku);
        boolean low = stock < min;
        StockAlertState state = stockAlertStateMapper.selectById(skuId);
        boolean alerting = state != null && state.getAlerting() != null && state.getAlerting() == 1;
        if (low && !alerting) {
            Product product = productMapper.selectById(sku.getProductId());
            String name = product == null ? sku.getTitle() : product.getName() + " " + sku.getTitle();
            int transit = inTransit().getOrDefault(skuId, 0);
            int suggest = TradeMath.suggest(min, costPriceService.setting().getSuggestMultiplier() == null ? 2 : costPriceService.setting().getSuggestMultiplier(), stock, transit);
            noticeService.bossNotice(
                    "库存不足 " + name,
                    name + " 库存只剩 " + stock + "，低于最低库存 " + min + "。建议采购 " + suggest + "。",
                    "STOCK",
                    skuId,
                    "STOCK_LOW"
            );
        }
        if (state == null) {
            StockAlertState created = new StockAlertState();
            created.setSkuId(skuId);
            created.setAlerting(low ? 1 : 0);
            created.setUpdatedAt(DateTimes.now());
            stockAlertStateMapper.insert(created);
            return;
        }
        if (alerting == low) {
            return;
        }
        state.setAlerting(low ? 1 : 0);
        state.setUpdatedAt(DateTimes.now());
        stockAlertStateMapper.updateById(state);
    }

    /** 草稿采购单上的数量算在途，还没入库。 */
    public Map<Long, Integer> inTransit() {
        List<PurchaseOrder> drafts = purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getStatus, "DRAFT"));
        Map<Long, Integer> sums = new HashMap<>();
        if (drafts.isEmpty()) {
            return sums;
        }
        List<Long> ids = drafts.stream().map(PurchaseOrder::getId).toList();
        List<PurchaseItem> items = purchaseItemMapper.selectList(new LambdaQueryWrapper<PurchaseItem>()
                .in(PurchaseItem::getPurchaseId, ids));
        for (PurchaseItem item : items) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            sums.merge(item.getSkuId(), qty, Integer::sum);
        }
        return sums;
    }
}
