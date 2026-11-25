package com.zhongbang.controller.service.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.pojo.product.dto.ProductRequests;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.product.vo.ProductVo;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.service.stock.StockAlertService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品。小程序只看上架的；管理端可以新增、改资料、改价、上下架和删除。
 * 图片只保存地址。一个商品至少要有一条规格，价格和库存写在规格上。
 */
@Service
public class ProductService {

    private static final Map<Integer, String> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put(1, "家用多功能");
        CATEGORIES.put(2, "工业平缝");
        CATEGORIES.put(3, "包缝绷缝");
        CATEGORIES.put(4, "特种机");
    }

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final StockAlertService stockAlertService;

    public ProductService(ProductMapper productMapper, ProductSkuMapper productSkuMapper, StockAlertService stockAlertService) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.stockAlertService = stockAlertService;
    }

    /** 公开列表。下架和已删除的不会出现。可按分类编号、关键字筛选。 */
    public PageResult<ProductVo.Card> publicList(Integer categoryId, String keyword, int page, int size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<Product> wrapper = onShelf();
        if (categoryId != null && categoryId > 0) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(w -> w.like(Product::getName, like).or().like(Product::getModel, like));
        }
        wrapper.orderByDesc(Product::getId);
        Page<Product> result = productMapper.selectPage(new Page<>(p, s), wrapper);
        Map<Long, List<ProductSku>> skus = skusOf(result.getRecords());
        List<ProductVo.Card> list = result.getRecords().stream().map(item -> card(item, skus.get(item.getId()))).toList();
        return PageResult.of(list, result.getTotal(), p, s);
    }

    /** 公开详情。下架商品对客户返回 404，避免把未卖的型号露出去。 */
    public ProductVo.Detail publicDetail(long id) {
        Product product = productMapper.selectById(id);
        if (product == null || !"ON".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return detail(product, skus(product.getId()));
    }

    public List<ProductVo.Category> categories() {
        List<Product> products = productMapper.selectList(onShelf());
        List<ProductVo.Category> list = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : CATEGORIES.entrySet()) {
            long count = products.stream().filter(item -> entry.getKey().equals(item.getCategoryId())).count();
            list.add(new ProductVo.Category(entry.getKey(), entry.getValue(), count));
        }
        return list;
    }

    public Map<String, Object> home() {
        List<ProductVo.Category> categories = categories();
        PageResult<ProductVo.Card> hot = publicList(null, null, 1, 8);
        return Map.of(
                "banners", List.of(Map.of(
                        "id", 1,
                        "image", "",
                        "title", "新机到家，师傅上门",
                        "subtitle", "提交订单后由门店确认价格，支持线下收款",
                        "linkType", "PRODUCT_LIST",
                        "linkValue", "0"
                )),
                "quickEntries", List.of(
                        Map.of("code", "BUY", "title", "买机器", "icon", "机", "badge", ""),
                        Map.of("code", "REPAIR", "title", "一键报修", "icon", "修", "badge", ""),
                        Map.of("code", "ASSET", "title", "我的设备", "icon", "档", "badge", ""),
                        Map.of("code", "INVOICE", "title", "发票/对账", "icon", "票", "badge", "")
                ),
                "categories", categories,
                "hotProducts", hot.list()
        );
    }

    public PageResult<ProductVo.Admin> adminList(String keyword, String status, int page, int size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>().orderByDesc(Product::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Product::getStatus, status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(w -> w.like(Product::getName, like).or().like(Product::getModel, like));
        }
        Page<Product> result = productMapper.selectPage(new Page<>(p, s), wrapper);
        Map<Long, List<ProductSku>> skus = skusOf(result.getRecords());
        List<ProductVo.Admin> list = result.getRecords().stream()
                .map(item -> admin(item, skus.get(item.getId())))
                .toList();
        return PageResult.of(list, result.getTotal(), p, s);
    }

    public ProductVo.Admin adminGet(long id) {
        return admin(require(id), skus(id));
    }

    @Transactional
    public ProductVo.Admin create(ProductRequests.Save request) {
        Product product = new Product();
        product.setTenantId(1L);
        product.setDeleted(0);
        product.setCreatedAt(DateTimes.now());
        apply(product, request, true);
        productMapper.insert(product);
        replaceSkus(product.getId(), request.getSkus(), true);
        watchStock(product.getId());
        return adminGet(product.getId());
    }

    @Transactional
    public ProductVo.Admin update(long id, ProductRequests.Save request) {
        Product product = require(id);
        apply(product, request, false);
        productMapper.updateById(product);
        if (request.getSkus() != null) {
            replaceSkus(id, request.getSkus(), false);
            watchStock(id);
        }
        return adminGet(id);
    }

    /** 逻辑删除商品和它的规格。已经下过的订单留着自己的名称快照，不受影响。 */
    @Transactional
    public void delete(long id) {
        require(id);
        productMapper.deleteById(id);
        List<ProductSku> skus = skus(id);
        for (ProductSku sku : skus) {
            productSkuMapper.deleteById(sku.getId());
        }
    }

    @Transactional
    public ProductVo.Admin shelf(long id, boolean on) {
        Product product = require(id);
        product.setStatus(on ? "ON" : "OFF");
        product.setUpdatedAt(DateTimes.now());
        productMapper.updateById(product);
        return adminGet(id);
    }

    /** 改某一条规格的单价。不传 skuId 时改第一条。 */
    @Transactional
    public ProductVo.Admin changePrice(long id, ProductRequests.Price request) {
        require(id);
        if (request == null || request.getPrice() == null || request.getPrice() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "价格不能为负");
        }
        List<ProductSku> skus = skus(id);
        if (skus.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品没有规格");
        }
        ProductSku target = skus.get(0);
        if (request.getSkuId() != null) {
            target = skus.stream().filter(sku -> request.getSkuId().equals(sku.getId())).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品规格不存在"));
        }
        target.setPrice(request.getPrice());
        target.setUpdatedAt(DateTimes.now());
        productSkuMapper.updateById(target);
        Product product = require(id);
        product.setUpdatedAt(DateTimes.now());
        productMapper.updateById(product);
        return adminGet(id);
    }

    private void apply(Product product, ProductRequests.Save request, boolean creating) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写商品名称");
        }
        if (creating && (request.getSkus() == null || request.getSkus().isEmpty())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少填写一条规格");
        }
        product.setName(request.getName().trim());
        product.setBrand(blankToNull(request.getBrand()));
        product.setModel(blankToNull(request.getModel()));
        product.setSubtitle(blankToNull(request.getSubtitle()));
        product.setCoverImage(request.getCoverImage() == null ? "" : request.getCoverImage().trim());
        product.setTags(blankToNull(request.getTags()));
        Integer categoryId = request.getCategoryId();
        String category = blankToNull(request.getCategory());
        if (categoryId != null && CATEGORIES.containsKey(categoryId) && category == null) {
            category = CATEGORIES.get(categoryId);
        }
        product.setCategoryId(categoryId);
        product.setCategory(category);
        String status = request.getStatus() == null ? null : request.getStatus().trim().toUpperCase();
        if (status == null || status.isEmpty()) {
            if (creating) {
                status = "ON";
            } else {
                status = product.getStatus();
            }
        }
        if (!"ON".equals(status) && !"OFF".equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上下架状态只能是 ON 或 OFF");
        }
        product.setStatus(status);
        product.setUpdatedAt(DateTimes.now());
    }

    private void replaceSkus(long productId, List<ProductRequests.Sku> requests, boolean creating) {
        if (requests == null) {
            return;
        }
        if (requests.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少填写一条规格");
        }
        List<ProductSku> existing = skus(productId);
        List<Long> kept = new ArrayList<>();
        for (ProductRequests.Sku request : requests) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写规格名称");
            }
            if (request.getPrice() == null || request.getPrice() < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "价格不能为负");
            }
            if (request.getStock() == null || request.getStock() < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "库存不能为负");
            }
            ProductSku sku = null;
            if (request.getId() != null) {
                sku = existing.stream().filter(item -> request.getId().equals(item.getId())).findFirst().orElse(null);
            }
            if (sku == null) {
                sku = new ProductSku();
                sku.setProductId(productId);
                sku.setLocked(0);
                sku.setDeleted(0);
                sku.setCreatedAt(DateTimes.now());
                sku.setTitle(request.getTitle().trim());
                sku.setPrice(request.getPrice());
                sku.setStock(request.getStock());
                sku.setUpdatedAt(DateTimes.now());
                productSkuMapper.insert(sku);
            } else {
                sku.setTitle(request.getTitle().trim());
                sku.setPrice(request.getPrice());
                sku.setStock(request.getStock());
                sku.setUpdatedAt(DateTimes.now());
                productSkuMapper.updateById(sku);
            }
            kept.add(sku.getId());
        }
        if (!creating) {
            for (ProductSku sku : existing) {
                if (!kept.contains(sku.getId())) {
                    productSkuMapper.deleteById(sku.getId());
                }
            }
        }
    }

    private Product require(long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private void watchStock(long productId) {
        for (ProductSku sku : skus(productId)) {
            stockAlertService.evaluate(sku.getId());
        }
    }

    private List<ProductSku> skus(long productId) {
        return productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, productId)
                .orderByAsc(ProductSku::getId));
    }

    private Map<Long, List<ProductSku>> skusOf(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        List<ProductSku> skus = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().in(ProductSku::getProductId, ids));
        Map<Long, List<ProductSku>> grouped = new LinkedHashMap<>();
        for (ProductSku sku : skus) {
            grouped.computeIfAbsent(sku.getProductId(), key -> new ArrayList<>()).add(sku);
        }
        for (List<ProductSku> list : grouped.values()) {
            list.sort(Comparator.comparing(ProductSku::getId));
        }
        return grouped;
    }

    private ProductVo.Card card(Product product, List<ProductSku> skus) {
        List<ProductSku> rows = skus == null ? List.of() : skus;
        return new ProductVo.Card(
                product.getId(),
                product.getName(),
                product.getModel(),
                product.getCoverImage(),
                minPrice(rows),
                0,
                stock(rows),
                Jsons.splitComma(product.getTags()),
                product.getCategoryId(),
                product.getSubtitle(),
                product.getBrand(),
                0,
                product.getStatus()
        );
    }

    private ProductVo.Detail detail(Product product, List<ProductSku> skus) {
        ProductVo.Card brief = card(product, skus);
        return new ProductVo.Detail(
                brief.id(),
                brief.name(),
                brief.model(),
                brief.coverImage(),
                brief.price(),
                0,
                brief.stock(),
                brief.tags(),
                brief.categoryId(),
                product.getCategory(),
                brief.subtitle(),
                brief.brand(),
                0,
                brief.status(),
                new ProductVo.Warranty(12, 24, "整机 12 个月，电机电控 24 个月。安装费下单后由门店确认。"),
                List.of("提交订单后由门店确认价格", "支持先发货后收款"),
                List.of(),
                skus.stream().map(sku -> new ProductVo.Sku(sku.getId(), sku.getTitle(), sku.getTitle(), nz(sku.getPrice()), sku.getStock() == null ? 0 : sku.getStock())).toList()
        );
    }

    private ProductVo.Admin admin(Product product, List<ProductSku> skus) {
        List<ProductSku> rows = skus == null ? List.of() : skus;
        return new ProductVo.Admin(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getCategoryId(),
                product.getModel(),
                product.getSubtitle(),
                product.getCoverImage(),
                product.getTags(),
                product.getStatus(),
                minPrice(rows),
                stock(rows),
                DateTimes.format(product.getCreatedAt()),
                DateTimes.format(product.getUpdatedAt()),
                rows.stream().map(sku -> new ProductVo.AdminSku(
                        sku.getId(),
                        sku.getTitle(),
                        nz(sku.getPrice()),
                        sku.getStock() == null ? 0 : sku.getStock(),
                        StaffRoles.isBoss(UserContext.currentOrNull()) ? sku.getCostPrice() : null,
                        sku.getMinStock()
                )).toList()
        );
    }

    private LambdaQueryWrapper<Product> onShelf() {
        return new LambdaQueryWrapper<Product>().eq(Product::getStatus, "ON");
    }

    private static long minPrice(List<ProductSku> skus) {
        return skus.stream().map(ProductSku::getPrice).filter(price -> price != null).mapToLong(Long::longValue).min().orElse(0L);
    }

    private static int stock(List<ProductSku> skus) {
        return skus.stream().map(ProductSku::getStock).filter(value -> value != null).mapToInt(Integer::intValue).sum();
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
