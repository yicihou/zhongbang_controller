package com.zhongbang.controller.service.importdata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.mapper.order.OrderLineMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.purchase.SupplierMapper;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.pojo.importdata.vo.ImportVo;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseItem;
import com.zhongbang.controller.pojo.purchase.entity.PurchaseOrder;
import com.zhongbang.controller.pojo.purchase.entity.Supplier;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.service.cost.CostPriceService;
import com.zhongbang.controller.service.purchase.PurchaseService;
import com.zhongbang.controller.service.stock.StockAlertService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Excel / CSV 导入。用户表的列名不一定和系统一样，所以先预览，让用户把列对上，再确认。
 * 某一行写不进去时记下原因，继续后面的行。
 * 历史采购和历史销售默认不改当前库存，避免和「商品库存」导进来的数字加两次。
 * 采购模板里「调整库存」填「是」才会加库存。
 */
@Service
public class TradeImportService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int LIMIT = 500;

    private final Map<String, Stored> previews = new ConcurrentHashMap<>();
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SupplierMapper supplierMapper;
    private final CustomerMapper customerMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final OrderLineMapper orderLineMapper;
    private final PaymentBillMapper paymentBillMapper;
    private final PurchaseService purchaseService;
    private final CostPriceService costPriceService;
    private final StockAlertService stockAlertService;
    private final IdentifierGenerator idGenerator;

    public TradeImportService(
            ProductMapper productMapper,
            ProductSkuMapper productSkuMapper,
            SupplierMapper supplierMapper,
            CustomerMapper customerMapper,
            SalesOrderMapper salesOrderMapper,
            OrderLineMapper orderLineMapper,
            PaymentBillMapper paymentBillMapper,
            PurchaseService purchaseService,
            CostPriceService costPriceService,
            StockAlertService stockAlertService,
            IdentifierGenerator idGenerator
    ) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.supplierMapper = supplierMapper;
        this.customerMapper = customerMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.orderLineMapper = orderLineMapper;
        this.paymentBillMapper = paymentBillMapper;
        this.purchaseService = purchaseService;
        this.costPriceService = costPriceService;
        this.stockAlertService = stockAlertService;
        this.idGenerator = idGenerator;
    }

    public byte[] template(String kind, String format) {
        StaffRoles.requireBoss("只有老板可以下载导入模板");
        List<ImportVo.Field> fields = fields(kind);
        String[] headers = fields.stream().map(ImportVo.Field::label).toArray(String[]::new);
        if ("xlsx".equalsIgnoreCase(format)) {
            try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = book.createSheet("模板");
                Row row = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    row.createCell(i).setCellValue(headers[i]);
                }
                book.write(out);
                return out.toByteArray();
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板生成失败");
            }
        }
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(",", headers)).append('\n');
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ImportVo.Preview preview(String kind, MultipartFile file) {
        StaffRoles.requireBoss("只有老板可以导入");
        String normalized = normalizeKind(kind);
        Parsed parsed = read(file);
        Map<String, String> mapping = suggest(parsed.headers(), fields(normalized));
        String token = UUID.randomUUID().toString().replace("-", "");
        previews.put(token, new Stored(normalized, parsed.headers(), parsed.rows()));
        return new ImportVo.Preview(token, normalized, fields(normalized), parsed.headers(), mapping, previewRows(normalized, parsed.rows(), mapping));
    }

    @Transactional
    public ImportVo.CommitResult commit(String token, Map<String, String> mapping) {
        StaffRoles.requireBoss("只有老板可以导入");
        Stored stored = previews.get(token);
        if (stored == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "预览已失效，请重新上传");
        }
        Map<String, String> used = mapping == null || mapping.isEmpty() ? suggest(stored.headers(), fields(stored.kind())) : mapping;
        List<ImportVo.PreviewRow> checked = previewRows(stored.kind(), stored.rows(), used);
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        if ("PURCHASE".equals(stored.kind()) || "SALE".equals(stored.kind())) {
            created = commitGrouped(stored.kind(), checked, used, errors);
            skipped = countSkipped(checked, errors);
        } else {
            for (ImportVo.PreviewRow row : checked) {
                if (row.error() != null) {
                    skipped++;
                    remember(errors, "第 " + row.rowNo() + " 行：" + row.error());
                    continue;
                }
                try {
                    if ("PRODUCT".equals(stored.kind())) {
                        importProduct(values(row.cells(), used));
                    } else {
                        importSupplier(values(row.cells(), used));
                    }
                    created++;
                } catch (BusinessException ex) {
                    skipped++;
                    remember(errors, "第 " + row.rowNo() + " 行：" + ex.getMessage());
                }
            }
        }
        if (created > 0) {
            previews.remove(token);
        }
        return new ImportVo.CommitResult(created, skipped, errors);
    }

    private int commitGrouped(String kind, List<ImportVo.PreviewRow> rows, Map<String, String> mapping, List<String> errors) {
        Map<String, List<ImportVo.PreviewRow>> groups = new LinkedHashMap<>();
        for (ImportVo.PreviewRow row : rows) {
            if (row.error() != null) {
                remember(errors, "第 " + row.rowNo() + " 行：" + row.error());
                continue;
            }
            Map<String, String> values = values(row.cells(), mapping);
            String key = "PURCHASE".equals(kind) ? values.getOrDefault("purchaseNo", "") : values.getOrDefault("orderNo", "");
            if (key.isBlank()) {
                key = "ROW-" + row.rowNo();
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        int created = 0;
        for (List<ImportVo.PreviewRow> group : groups.values()) {
            try {
                if ("PURCHASE".equals(kind)) {
                    importPurchase(group, mapping);
                } else {
                    importSale(group, mapping);
                }
                created++;
            } catch (BusinessException ex) {
                for (ImportVo.PreviewRow row : group) {
                    remember(errors, "第 " + row.rowNo() + " 行：" + ex.getMessage());
                }
            }
        }
        return created;
    }

    private int countSkipped(List<ImportVo.PreviewRow> rows, List<String> errors) {
        return errors.size();
    }

    private void importProduct(Map<String, String> values) {
        String name = required(values, "productName", "商品名称");
        String skuName = required(values, "skuName", "规格");
        String model = blank(values.get("model"));
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getName, name)
                .eq(model != null, Product::getModel, model)
                .last("limit 1"));
        if (product == null) {
            product = new Product();
            product.setTenantId(1L);
            product.setName(name);
            product.setModel(model);
            product.setBrand(blank(values.get("brand")));
            product.setStatus("ON");
            product.setDeleted(0);
            product.setCreatedAt(DateTimes.now());
            product.setUpdatedAt(DateTimes.now());
            productMapper.insert(product);
        }
        ProductSku sku = productSkuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, product.getId())
                .eq(ProductSku::getTitle, skuName)
                .last("limit 1"));
        boolean fresh = sku == null;
        if (fresh) {
            sku = new ProductSku();
            sku.setProductId(product.getId());
            sku.setTitle(skuName);
            sku.setPrice(0L);
            sku.setStock(0);
            sku.setLocked(0);
            sku.setDeleted(0);
            sku.setCreatedAt(DateTimes.now());
        }
        Long price = parseYuan(values.get("salePrice"));
        if (price != null) {
            sku.setPrice(price);
        }
        Integer stock = parseInt(values.get("stock"));
        if (stock != null) {
            if (stock < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "库存不能为负");
            }
            sku.setStock(stock);
        }
        Integer min = parseInt(values.get("minStock"));
        if (min != null) {
            sku.setMinStock(min);
        }
        sku.setUpdatedAt(DateTimes.now());
        if (fresh) {
            productSkuMapper.insert(sku);
        } else {
            productSkuMapper.updateById(sku);
        }
        Long cost = parseYuan(values.get("costPrice"));
        if (cost != null) {
            costPriceService.change(sku, cost, UserContext.require().getDisplayName(), "导入");
        }
        stockAlertService.evaluate(sku.getId());
    }

    private void importSupplier(Map<String, String> values) {
        String name = required(values, "name", "名称");
        Supplier row = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>().eq(Supplier::getName, name).last("limit 1"));
        boolean fresh = row == null;
        if (fresh) {
            row = new Supplier();
            row.setName(name);
            row.setSource("IMPORT");
            row.setDeleted(0);
            row.setCreatedAt(DateTimes.now());
        }
        row.setContact(blank(values.get("contact")));
        row.setMobile(blank(values.get("mobile")));
        row.setBrands(blank(values.get("brands")));
        row.setAccountDays(parseInt(values.get("accountDays")));
        row.setRemark(blank(values.get("remark")));
        row.setUpdatedAt(DateTimes.now());
        if (fresh) {
            supplierMapper.insert(row);
        } else {
            supplierMapper.updateById(row);
        }
    }

    private void importPurchase(List<ImportVo.PreviewRow> rows, Map<String, String> mapping) {
        Map<String, String> first = values(rows.get(0).cells(), mapping);
        String supplierName = required(first, "supplierName", "供应商");
        Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>().eq(Supplier::getName, supplierName).last("limit 1"));
        if (supplier == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到供应商「" + supplierName + "」");
        }
        PurchaseOrder order = new PurchaseOrder();
        String no = blank(first.get("purchaseNo"));
        order.setPurchaseNo(no == null ? null : no);
        order.setSupplierId(supplier.getId());
        order.setOrderDate(parseDate(first.get("orderDate")));
        order.setStatus("RECEIVED");
        order.setSource("IMPORT");
        order.setOperatorName(UserContext.require().getDisplayName());
        applyHeaderInvoice(order, first);
        boolean adjust = truthy(first.get("adjustStock"));
        long freight = 0;
        Long freightYuan = parseYuan(first.get("freight"));
        if (freightYuan != null) {
            freight = freightYuan;
        }
        order.setFreightAmount(freight);
        List<PurchaseItem> lines = new ArrayList<>();
        long goods = 0;
        boolean priced = true;
        for (ImportVo.PreviewRow row : rows) {
            Map<String, String> values = values(row.cells(), mapping);
            ProductSku sku = findSku(required(values, "skuName", "规格"));
            int qty = parseIntRequired(values.get("quantity"), "数量");
            Long cost = parseYuan(values.get("unitCost"));
            PurchaseItem line = new PurchaseItem();
            line.setSkuId(sku.getId());
            Product product = productMapper.selectById(sku.getProductId());
            line.setProductName(product == null ? sku.getTitle() : product.getName());
            line.setSkuName(sku.getTitle());
            line.setQuantity(qty);
            line.setUnitCost(cost);
            if (cost == null) {
                priced = false;
                line.setLineAmount(null);
            } else {
                line.setLineAmount(cost * qty);
                goods += line.getLineAmount();
            }
            lines.add(line);
        }
        order.setGoodsAmount(goods);
        order.setTotalAmount(priced ? goods + freight : goods + freight);
        purchaseService.insertReady(order, lines, adjust, "导入");
    }

    private void importSale(List<ImportVo.PreviewRow> rows, Map<String, String> mapping) {
        Map<String, String> first = values(rows.get(0).cells(), mapping);
        Customer customer = customer(first);
        SalesOrder order = new SalesOrder();
        order.setId(nextId());
        order.setTenantId(1L);
        String no = blank(first.get("orderNo"));
        order.setOrderNo(no == null ? nextOrderNo() : no);
        if (salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>().eq(SalesOrder::getOrderNo, order.getOrderNo())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "单号已存在：" + order.getOrderNo());
        }
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getName());
        order.setCustomerMobile(customer.getMobile());
        order.setStatus("COMPLETED");
        order.setSource("IMPORT");
        order.setDeleted(0);
        order.setDepositAmount(0L);
        order.setFreightAmount(0L);
        order.setAdjustAmount(0L);
        order.setPriceAdjusted(0);
        order.setAddressText("导入的历史订单");
        LocalDateTime when = parseDateTime(first.get("orderDate"));
        order.setCreatedAt(when);
        order.setUpdatedAt(when);
        boolean invoiced = truthy(first.get("invoiced"));
        order.setInvoiced(invoiced ? 1 : 0);
        order.setInvoiceType(invoiced ? blank(first.get("invoiceType")) : null);
        order.setTaxRate(invoiced ? parseRate(first.get("taxRate")) : null);
        Boolean included = taxIncluded(first.get("taxIncluded"));
        order.setTaxIncluded(!invoiced || included == null ? null : (included ? 1 : 0));
        long goods = 0;
        long install = 0;
        List<OrderLine> lines = new ArrayList<>();
        for (ImportVo.PreviewRow row : rows) {
            Map<String, String> values = values(row.cells(), mapping);
            ProductSku sku = findSku(required(values, "skuName", "规格"));
            Product product = productMapper.selectById(sku.getProductId());
            int qty = parseIntRequired(values.get("quantity"), "数量");
            Long price = parseYuan(values.get("unitPrice"));
            if (price == null) {
                price = sku.getPrice() == null ? 0L : sku.getPrice();
            }
            Long installFee = parseYuan(values.get("installFee"));
            if (installFee == null) {
                installFee = 0L;
            }
            OrderLine line = new OrderLine();
            line.setId(nextId());
            line.setOrderId(order.getId());
            line.setSkuId(sku.getId());
            line.setProductName(product == null ? sku.getTitle() : product.getName());
            line.setSkuName(sku.getTitle());
            line.setQuantity(qty);
            line.setUnitPrice(price);
            line.setLineAmount(price * qty);
            line.setDeliveryMethod("STORE_PICKUP");
            line.setInstallFee(installFee);
            Long cost = parseYuan(values.get("costPrice"));
            if (cost == null && !values.containsKey("costPrice")) {
                cost = sku.getCostPrice();
            }
            if (cost == null) {
                line.setCostKnown(0);
            } else {
                line.setCostKnown(1);
                line.setCostPrice(cost);
                line.setCostAmount(cost * qty);
            }
            lines.add(line);
            goods += line.getLineAmount();
            install += installFee;
        }
        order.setGoodsAmount(goods);
        order.setInstallAmount(install);
        order.setPayableAmount(goods + install);
        order.setTotalAmount(order.getPayableAmount());
        Long paid = parseYuan(first.get("paidAmount"));
        long paidAmount = paid == null ? order.getPayableAmount() : paid;
        order.setPaidAmount(paidAmount);
        order.setBalanceAmount(Math.max(0, order.getPayableAmount() - paidAmount));
        salesOrderMapper.insert(order);
        for (OrderLine line : lines) {
            orderLineMapper.insert(line);
        }
        if (paidAmount > 0) {
            PaymentBill bill = new PaymentBill();
            bill.setId(nextId());
            bill.setTenantId(1L);
            bill.setOrderId(order.getId());
            bill.setBizType("ORDER");
            bill.setBizId(order.getId());
            bill.setOutTradeNo(order.getOrderNo() + "-IMP");
            bill.setPaymentNo(order.getOrderNo() + "-IMP");
            bill.setType("FULL");
            bill.setTitle("导入");
            bill.setAmount(paidAmount);
            bill.setChannel("OFFLINE");
            bill.setStatus("SUCCESS");
            bill.setSortNo(1);
            bill.setPaidAt(when);
            bill.setCreatedAt(when);
            bill.setUpdatedAt(when);
            paymentBillMapper.insert(bill);
        }
    }

    private Customer customer(Map<String, String> values) {
        String mobile = blank(values.get("mobile"));
        String name = blank(values.get("customerName"));
        if (name == null) {
            name = "导入客户";
        }
        Customer found = null;
        if (mobile != null) {
            found = customerMapper.selectOne(new LambdaQueryWrapper<Customer>().eq(Customer::getMobile, mobile).last("limit 1"));
        }
        if (found == null) {
            found = customerMapper.selectOne(new LambdaQueryWrapper<Customer>().eq(Customer::getName, name).last("limit 1"));
        }
        if (found != null) {
            return found;
        }
        Customer created = new Customer();
        created.setTenantId(1L);
        created.setName(name);
        created.setMobile(mobile);
        created.setLevel(1);
        created.setLevelCode("NORMAL");
        created.setLevelManual(0);
        created.setCustomerType("PERSONAL");
        created.setTotalPaid(0L);
        created.setSource("IMPORT");
        created.setDeleted(0);
        created.setCreatedAt(DateTimes.now());
        created.setUpdatedAt(DateTimes.now());
        customerMapper.insert(created);
        return created;
    }

    private ProductSku findSku(String title) {
        List<ProductSku> found = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getTitle, title));
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到规格「" + title + "」");
        }
        if (found.size() > 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "规格名「" + title + "」不唯一");
        }
        return found.get(0);
    }

    private void applyHeaderInvoice(PurchaseOrder order, Map<String, String> values) {
        boolean on = truthy(values.get("invoiced"));
        order.setInvoiced(on ? 1 : 0);
        order.setInvoiceType(on ? blank(values.get("invoiceType")) : null);
        order.setTaxRate(on ? parseRate(values.get("taxRate")) : null);
        Boolean included = taxIncluded(values.get("taxIncluded"));
        order.setTaxIncluded(!on || included == null ? null : (included ? 1 : 0));
    }

    private List<ImportVo.PreviewRow> previewRows(String kind, List<Map<String, String>> rows, Map<String, String> mapping) {
        List<ImportVo.PreviewRow> list = new ArrayList<>();
        int number = 2;
        for (Map<String, String> row : rows) {
            String error = null;
            try {
                Map<String, String> values = values(row, mapping);
                for (ImportVo.Field field : fields(kind)) {
                    if (field.required() && blank(values.get(field.key())) == null) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少「" + field.label() + "」");
                    }
                }
                if (values.containsKey("salePrice") || "PRODUCT".equals(kind)) {
                    parseYuan(values.get("salePrice"));
                    parseYuan(values.get("costPrice"));
                }
                parseYuan(values.get("unitCost"));
                parseYuan(values.get("unitPrice"));
                parseYuan(values.get("freight"));
                parseRate(values.get("taxRate"));
            } catch (BusinessException ex) {
                error = ex.getMessage();
            } catch (RuntimeException ex) {
                error = "这一行读不出来";
            }
            list.add(new ImportVo.PreviewRow(number++, row, error));
        }
        return list;
    }

    private Parsed read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要导入的文件");
        }
        try {
            byte[] bytes = file.getBytes();
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            if (name.endsWith(".xlsx") || (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K')) {
                return readXlsx(bytes);
            }
            return readCsv(new String(stripBom(bytes), StandardCharsets.UTF_8));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取文件，请使用下载的模板");
        }
    }

    private Parsed readCsv(String text) {
        String[] lines = text.split("\\r?\\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件是空的");
        }
        List<String> headers = splitCsv(lines[0]);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            if (rows.size() >= LIMIT) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多导入 " + LIMIT + " 行");
            }
            List<String> cells = splitCsv(lines[i]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), c < cells.size() ? cells.get(c).trim() : "");
            }
            rows.add(row);
        }
        return new Parsed(headers, rows);
    }

    private Parsed readXlsx(byte[] bytes) throws Exception {
        DataFormatter formatter = new DataFormatter();
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = book.getNumberOfSheets() == 0 ? null : book.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件是空的");
            }
            Row head = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < head.getLastCellNum(); c++) {
                String title = formatter.formatCellValue(head.getCell(c)).trim();
                if (!title.isEmpty()) {
                    headers.add(title);
                }
            }
            if (headers.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "第一行要是列名");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < headers.size(); c++) {
                    String value = formatter.formatCellValue(row.getCell(c)).trim();
                    if (!value.isEmpty()) {
                        any = true;
                    }
                    map.put(headers.get(c), value);
                }
                if (!any) {
                    continue;
                }
                if (rows.size() >= LIMIT) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多导入 " + LIMIT + " 行");
                }
                rows.add(map);
            }
            return new Parsed(headers, rows);
        }
    }

    private static Map<String, String> suggest(List<String> headers, List<ImportVo.Field> fields) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (ImportVo.Field field : fields) {
            for (String header : headers) {
                if (header.equals(field.label()) || header.contains(field.label())) {
                    mapping.put(field.key(), header);
                    break;
                }
            }
        }
        return mapping;
    }

    private static Map<String, String> values(Map<String, String> cells, Map<String, String> mapping) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            values.put(entry.getKey(), cells.getOrDefault(entry.getValue(), ""));
        }
        return values;
    }

    public static List<ImportVo.Field> fields(String kind) {
        return switch (normalizeKind(kind)) {
            case "SUPPLIER" -> List.of(
                    field("name", "名称", true),
                    field("contact", "联系人", false),
                    field("mobile", "电话", false),
                    field("brands", "主营品牌", false),
                    field("accountDays", "账期", false),
                    field("remark", "备注", false));
            case "PURCHASE" -> List.of(
                    field("supplierName", "供应商", true),
                    field("purchaseNo", "单号", false),
                    field("orderDate", "日期", false),
                    field("skuName", "规格", true),
                    field("quantity", "数量", true),
                    field("unitCost", "进价", false),
                    field("freight", "运费", false),
                    field("invoiced", "是否开票", false),
                    field("invoiceType", "发票类型", false),
                    field("taxRate", "税率", false),
                    field("taxIncluded", "含税", false),
                    field("adjustStock", "调整库存", false));
            case "SALE" -> List.of(
                    field("orderNo", "单号", false),
                    field("orderDate", "日期", false),
                    field("customerName", "客户", false),
                    field("mobile", "手机", false),
                    field("skuName", "规格", true),
                    field("quantity", "数量", true),
                    field("unitPrice", "单价", false),
                    field("installFee", "安装费", false),
                    field("costPrice", "成本单价", false),
                    field("invoiced", "是否开票", false),
                    field("invoiceType", "发票类型", false),
                    field("taxRate", "税率", false),
                    field("taxIncluded", "含税", false),
                    field("paidAmount", "已收", false));
            default -> List.of(
                    field("productName", "商品名称", true),
                    field("model", "型号", false),
                    field("brand", "品牌", false),
                    field("skuName", "规格", true),
                    field("salePrice", "售价", false),
                    field("costPrice", "进价", false),
                    field("stock", "库存", false),
                    field("minStock", "最低库存", false));
        };
    }

    private static ImportVo.Field field(String key, String label, boolean required) {
        return new ImportVo.Field(key, label, required);
    }

    private static String normalizeKind(String kind) {
        if (kind == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择导入类型");
        }
        return switch (kind.trim().toUpperCase()) {
            case "PRODUCT", "商品" -> "PRODUCT";
            case "SUPPLIER", "供应商" -> "SUPPLIER";
            case "PURCHASE", "采购" -> "PURCHASE";
            case "SALE", "销售" -> "SALE";
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "导入类型只能是商品、供应商、采购或销售");
        };
    }

    private static String required(Map<String, String> values, String key, String label) {
        String value = blank(values.get(key));
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少「" + label + "」");
        }
        return value;
    }

    static Long parseYuan(String raw) {
        String text = moneyText(raw);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金额「" + raw + "」无法识别，请填元");
        }
    }

    static Integer parseRate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim().replace("%", "");
        try {
            return new BigDecimal(text).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "税率「" + raw + "」无法识别");
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数字「" + raw + "」无法识别");
        }
    }

    private static int parseIntRequired(String raw, String label) {
        Integer value = parseInt(raw);
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "须大于 0");
        }
        return value;
    }

    private static String parseDate(String raw) {
        return parseDateTime(raw).toLocalDate().toString();
    }

    private static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now(SHANGHAI).atStartOfDay();
        }
        String text = raw.trim().replace('/', '-');
        try {
            if (text.length() >= 19) {
                return LocalDateTime.parse(text.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            if (text.matches("\\d{8}")) {
                return LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
            }
            return LocalDate.parse(text.substring(0, 10)).atStartOfDay();
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日期「" + raw + "」无法识别");
        }
    }

    private static boolean truthy(String raw) {
        if (raw == null) {
            return false;
        }
        String text = raw.trim();
        return "是".equals(text) || "开票".equals(text) || "1".equals(text) || "Y".equalsIgnoreCase(text)
                || "YES".equalsIgnoreCase(text) || "TRUE".equalsIgnoreCase(text);
    }

    private static Boolean taxIncluded(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.contains("不含")) {
            return false;
        }
        if (raw.contains("含")) {
            return true;
        }
        if ("0".equals(raw.trim())) {
            return false;
        }
        if ("1".equals(raw.trim())) {
            return true;
        }
        return null;
    }

    private static String moneyText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim().replace(",", "").replace("¥", "").replace("￥", "").replace("元", "");
        return text.isBlank() ? null : text;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            byte[] copy = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, copy, 0, copy.length);
            return copy;
        }
        return bytes;
    }

    private static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quote = !quote;
                continue;
            }
            if (ch == ',' && !quote) {
                cells.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private static void remember(List<String> errors, String message) {
        if (errors.size() < 30) {
            errors.add(message);
        }
    }

    private String nextOrderNo() {
        String day = LocalDate.now(SHANGHAI).format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "SO" + day;
        long count = salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>().likeRight(SalesOrder::getOrderNo, prefix));
        return prefix + String.format("%04d", count + 1);
    }

    private long nextId() {
        return idGenerator.nextId(null).longValue();
    }

    private record Parsed(List<String> headers, List<Map<String, String>> rows) {
    }

    private record Stored(String kind, List<String> headers, List<Map<String, String>> rows) {
    }
}
