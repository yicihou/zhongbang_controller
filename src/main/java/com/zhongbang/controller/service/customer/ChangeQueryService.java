package com.zhongbang.controller.service.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.pojo.customer.vo.ChangeVo;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.security.LoginUser;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.security.UserType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 给小程序轮询用的轻量检查：只回答「自从 since 之后，订单、报修、商品有没有变过」。
 * 不返回单据内容。页面发现某一类变了，再去拉原来的详情或列表。
 */
@Service
public class ChangeQueryService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final SalesOrderMapper salesOrderMapper;
    private final RepairTicketMapper repairTicketMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    public ChangeQueryService(SalesOrderMapper salesOrderMapper,
                              RepairTicketMapper repairTicketMapper,
                              ProductMapper productMapper,
                              ProductSkuMapper productSkuMapper) {
        this.salesOrderMapper = salesOrderMapper;
        this.repairTicketMapper = repairTicketMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
    }

    /**
     * @param since 上次拿到的 cursor，一串毫秒时间戳。不传时只发新的 cursor，changed 为 false，
     *              方便页面在刚加载完数据后记下起点，避免立刻再拉一遍。
     */
    public ChangeVo since(String since) {
        LoginUser user = UserContext.require();
        if (user.getUserType() != UserType.CUSTOMER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请使用客户账号");
        }
        LocalDateTime now = DateTimes.now();
        String cursor = String.valueOf(now.atZone(SHANGHAI).toInstant().toEpochMilli());
        LocalDateTime from = parse(since);
        if (from == null) {
            return new ChangeVo(cursor, false, false, false, false);
        }
        boolean orders = after(latestOrder(user.getUserId()), from);
        boolean repairs = after(latestRepair(user.getUserId()), from);
        boolean products = after(latestProduct(), from) || after(latestSku(), from);
        return new ChangeVo(cursor, orders || repairs || products, orders, repairs, products);
    }

    private LocalDateTime latestOrder(long customerId) {
        SalesOrder row = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerId, customerId)
                .orderByDesc(SalesOrder::getUpdatedAt)
                .last("LIMIT 1"));
        return row == null ? null : row.getUpdatedAt();
    }

    private LocalDateTime latestRepair(long customerId) {
        RepairTicket row = repairTicketMapper.selectOne(new LambdaQueryWrapper<RepairTicket>()
                .eq(RepairTicket::getCustomerId, customerId)
                .orderByDesc(RepairTicket::getUpdatedAt)
                .last("LIMIT 1"));
        return row == null ? null : row.getUpdatedAt();
    }

    private LocalDateTime latestProduct() {
        Product row = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .orderByDesc(Product::getUpdatedAt)
                .last("LIMIT 1"));
        return row == null ? null : row.getUpdatedAt();
    }

    private LocalDateTime latestSku() {
        ProductSku row = productSkuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .orderByDesc(ProductSku::getUpdatedAt)
                .last("LIMIT 1"));
        return row == null ? null : row.getUpdatedAt();
    }

    private static boolean after(LocalDateTime value, LocalDateTime from) {
        return value != null && value.isAfter(from);
    }

    private static LocalDateTime parse(String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        try {
            long millis = Long.parseLong(since.trim());
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), SHANGHAI);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "since 应为毫秒时间戳");
        }
    }
}
