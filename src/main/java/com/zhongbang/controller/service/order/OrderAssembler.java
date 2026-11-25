package com.zhongbang.controller.service.order;

import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.PriceLog;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.order.entity.Shipment;
import com.zhongbang.controller.pojo.order.vo.OrderVo;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.service.cost.TradeMath;

import java.util.ArrayList;
import java.util.List;

/**
 * 把内存里的订单翻译成页面要的 JSON 字段名。
 * 管理端要行、应付、改价记录；小程序要状态中文、总额和商品名。
 */
public final class OrderAssembler {

    private OrderAssembler() {
    }

    public static OrderVo.Admin admin(OrderBundle bundle) {
        SalesOrder order = bundle.getOrder();
        boolean boss = StaffRoles.isBoss(UserContext.currentOrNull());
        Long goodsCost = null;
        Long grossProfit = null;
        if (boss) {
            long cost = 0;
            long knownNet = 0;
            boolean any = false;
            for (OrderLine line : bundle.getItems()) {
                if (line.getCostKnown() != null && line.getCostKnown() == 1 && line.getCostAmount() != null) {
                    any = true;
                    cost += line.getCostAmount();
                    knownNet += TradeMath.split(nz(line.getLineAmount()), order.getInvoiced() != null && order.getInvoiced() == 1,
                            order.getTaxIncluded(), order.getTaxRate()).excluded();
                }
            }
            if (any) {
                goodsCost = cost;
                grossProfit = knownNet - cost;
            }
        }
        return new OrderVo.Admin(
                order.getId(),
                order.getOrderNo(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getCustomerMobile(),
                order.getStoreId(),
                order.getStatus(),
                nz(order.getGoodsAmount()),
                nz(order.getInstallAmount()),
                nz(order.getFreightAmount()),
                nz(order.getAdjustAmount()),
                nz(order.getPayableAmount()),
                nz(order.getPaidAmount()),
                order.getContactRemark(),
                order.getAdminRemark(),
                order.getPriceAdjusted() != null && order.getPriceAdjusted() == 1,
                order.getAddressText(),
                DateTimes.format(order.getCreatedAt()),
                DateTimes.format(order.getUpdatedAt()),
                bundle.getItems().stream().map(line -> item(line, boss)).toList(),
                bundle.getPayments().stream().map(OrderAssembler::payment).toList(),
                bundle.getPriceLogs().stream().map(OrderAssembler::log).toList(),
                order.getInvoiced() != null && order.getInvoiced() == 1,
                order.getInvoiceType(),
                order.getTaxRate(),
                order.getTaxIncluded() == null ? null : order.getTaxIncluded() == 1,
                goodsCost,
                grossProfit
        );
    }

    public static OrderVo.Created created(OrderBundle bundle) {
        SalesOrder order = bundle.getOrder();
        long payable = nz(order.getPayableAmount());
        long paid = nz(order.getPaidAmount());
        return new OrderVo.Created(
                order.getId(),
                order.getOrderNo(),
                payable,
                payable,
                paid,
                Math.max(0, payable - paid),
                order.getStatus()
        );
    }

    public static OrderVo.Mine mine(OrderBundle bundle) {
        SalesOrder order = bundle.getOrder();
        long payable = nz(order.getPayableAmount());
        long paid = nz(order.getPaidAmount());
        OrderLine install = bundle.getItems().stream()
                .filter(line -> "ON_SITE_INSTALL".equals(line.getDeliveryMethod()))
                .findFirst()
                .orElse(null);
        OrderLine techLine = bundle.getItems().stream()
                .filter(line -> line.getTechnicianName() != null && !line.getTechnicianName().isBlank())
                .findFirst()
                .orElse(null);
        String technician = techLine == null ? null : techLine.getTechnicianName();
        List<String> actions = new ArrayList<>();
        boolean shipped = bundle.getItems().stream().anyMatch(OrderRules::hasActiveShipment);
        boolean paidAny = bundle.getPayments().stream().anyMatch(p -> "SUCCESS".equals(p.getStatus()));
        if ("PENDING_PAYMENT".equals(order.getStatus()) && !shipped && !paidAny) {
            actions.add("CANCEL");
        }
        return new OrderVo.Mine(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                statusText(order.getStatus()),
                payable,
                payable,
                paid,
                Math.max(0, payable - paid),
                DateTimes.format(order.getCreatedAt()),
                install == null ? null : install.getInstallDate(),
                install == null ? null : install.getInstallSlot(),
                technician,
                order.getContactRemark(),
                new OrderVo.Address(order.getCustomerName(), order.getCustomerMobile(), order.getAddressText()),
                bundle.getItems().stream().map(line -> new OrderVo.MineItem(
                        line.getId(),
                        line.getSkuId(),
                        line.getProductName(),
                        line.getSkuName(),
                        line.getCoverImage(),
                        nz(line.getUnitPrice()),
                        line.getQuantity() == null ? 0 : line.getQuantity(),
                        line.getDeliveryMethod()
                )).toList(),
                actions,
                techLine == null ? null : new OrderVo.Tech(techLine.getTechnicianId(), techLine.getTechnicianName(), null),
                timeline(order.getStatus())
        );
    }

    /** 给小程序详情页用的简短进度，不是一张单独的状态机表。 */
    private static List<OrderVo.Step> timeline(String status) {
        String[] titles = {"提交订单", "待付款", "发货 / 安装", "完成"};
        int current = switch (status == null ? "" : status) {
            case "PARTIALLY_PAID", "PAID" -> 1;
            case "FULFILLING" -> 2;
            case "COMPLETED" -> 3;
            case "CANCELLED" -> 0;
            default -> 1;
        };
        if ("PENDING_PAYMENT".equals(status)) {
            current = 1;
        }
        List<OrderVo.Step> steps = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            steps.add(new OrderVo.Step(titles[i], titles[i], "", i < current || "COMPLETED".equals(status), i == current && !"COMPLETED".equals(status) && !"CANCELLED".equals(status)));
        }
        if ("CANCELLED".equals(status)) {
            steps.add(new OrderVo.Step("CANCELLED", "已取消", "", true, true));
        }
        return steps;
    }

    public static String statusText(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "PENDING_PAYMENT" -> "待付款";
            case "PARTIALLY_PAID" -> "部分付款";
            case "PAID" -> "已付款";
            case "FULFILLING" -> "履约中";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private static OrderVo.Item item(OrderLine line, boolean boss) {
        boolean known = line.getCostKnown() != null && line.getCostKnown() == 1;
        return new OrderVo.Item(
                line.getId(),
                line.getOrderId(),
                line.getSkuId(),
                line.getProductName(),
                line.getSkuName(),
                line.getCoverImage(),
                line.getQuantity() == null ? 0 : line.getQuantity(),
                nz(line.getUnitPrice()),
                nz(line.getLineAmount()),
                line.getDeliveryMethod(),
                line.getInstallFee(),
                line.getInstallDate(),
                line.getInstallSlot(),
                line.getTechnicianId(),
                line.getTechnicianName(),
                line.getAddressSnapshotJson(),
                line.getShipmentId(),
                shipment(line.getShipment()),
                line.getWorkOrderNo(),
                boss && known ? line.getCostPrice() : null,
                boss && known ? line.getCostAmount() : null,
                boss ? known : null
        );
    }

    private static OrderVo.Shipment shipment(Shipment row) {
        if (row == null) {
            return null;
        }
        return new OrderVo.Shipment(
                row.getId(),
                row.getOrderId(),
                row.getOrderItemId(),
                row.getMethod(),
                row.getStatus(),
                row.getCarrier(),
                row.getTrackingNo(),
                row.getLogisticsCompany(),
                row.getFreightNo(),
                row.getVehicleNo(),
                row.getCourierName(),
                row.getCourierMobile(),
                row.getAppointAt(),
                row.getPickupCode(),
                row.getPickupStoreId(),
                row.getPickedAt(),
                row.getRemark(),
                DateTimes.format(row.getCreatedAt()),
                DateTimes.format(row.getUpdatedAt())
        );
    }

    private static OrderVo.Payment payment(PaymentBill bill) {
        return new OrderVo.Payment(
                bill.getId(),
                bill.getBizType(),
                bill.getBizId(),
                bill.getPaymentNo(),
                bill.getOutTradeNo(),
                bill.getChannel(),
                bill.getType(),
                bill.getTitle(),
                nz(bill.getAmount()),
                bill.getStatus(),
                bill.getSortNo() == null ? 0 : bill.getSortNo(),
                Jsons.readStrings(bill.getVoucherUrls()),
                bill.getOfflineMethod(),
                DateTimes.format(bill.getPaidAt()),
                bill.getOperatorId(),
                bill.getOperatorName(),
                bill.getRemark(),
                bill.getSplitFromId(),
                DateTimes.format(bill.getCreatedAt()),
                DateTimes.format(bill.getUpdatedAt())
        );
    }

    private static OrderVo.PriceLog log(PriceLog row) {
        return new OrderVo.PriceLog(
                row.getId(),
                row.getOrderId(),
                row.getScope(),
                row.getOrderItemId(),
                row.getFieldName(),
                row.getBeforeAmount(),
                nz(row.getAfterAmount()),
                row.getReason(),
                row.getOperatorName(),
                DateTimes.format(row.getCreatedAt())
        );
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
