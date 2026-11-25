package com.zhongbang.controller.service.order;

import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.pojo.order.dto.OrderRequests;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.PriceLog;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.order.entity.Shipment;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 订单金额和状态规则，和 admin-web {@code domain/order.ts} 同一套。
 * 不访问数据库。调用方传入订单副本和发号器。
 *
 * <p>应付 = 商品行金额 + 上门安装费（未定价按 0）+ 运费 + 整单调整。
 * 待付款也可以发货、派师傅。已有未取消的发货就不能再发，也不能直接取消。
 * 线下少收时，差额拆成标题带「（余款）」的新待付。
 */
public final class OrderRules {

    public static final String SPLIT_SUFFIX = "（余款）";
    private static final Set<String> OFFLINE_METHODS = Set.of(
            "BANK_TRANSFER", "WECHAT_TRANSFER", "ALIPAY", "CASH", "OTHER");

    private OrderRules() {
    }

    /** 由服务提供下一个主键。规则里新建应付、发货、改价记录时用。 */
    public interface Ids {
        long next();
    }

    public static void recalc(OrderBundle bundle) {
        SalesOrder order = bundle.getOrder();
        long goods = 0;
        long install = 0;
        for (OrderLine item : bundle.getItems()) {
            goods += nz(item.getLineAmount());
            if ("ON_SITE_INSTALL".equals(item.getDeliveryMethod())) {
                install += nz(item.getInstallFee());
            }
        }
        order.setGoodsAmount(goods);
        order.setInstallAmount(install);
        order.setPayableAmount(goods + install + nz(order.getFreightAmount()) + nz(order.getAdjustAmount()));
    }

    /**
     * 收款和发货分开决定状态。已取消、已完成保持不动。
     * 还有待付且已付为 0：待付款，即使已经发货也不改成履约中。
     * 还有待付且已付大于 0：部分付款。没有待付且已付不少于应付：按发货进度变成已付款、履约中或已完成。
     */
    public static void syncStatus(OrderBundle bundle) {
        SalesOrder order = bundle.getOrder();
        long paid = paidAmount(bundle);
        order.setPaidAmount(paid);
        if ("CANCELLED".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) {
            mirrorLegacy(order);
            return;
        }
        List<PaymentBill> waits = waits(bundle);
        if (waits.isEmpty() && paid >= nz(order.getPayableAmount())) {
            Progress progress = progress(bundle);
            if (progress.total > 0 && progress.done == progress.total) {
                order.setStatus("COMPLETED");
            } else {
                order.setStatus(fulfillmentStarted(bundle) ? "FULFILLING" : "PAID");
            }
        } else if (!waits.isEmpty() && paid > 0) {
            order.setStatus("PARTIALLY_PAID");
        } else {
            order.setStatus("PENDING_PAYMENT");
        }
        mirrorLegacy(order);
    }

    /** 已发货行数 / 已完成行数 / 总行数。待发出和已取消不算已发货。 */
    public static Progress progress(OrderBundle bundle) {
        int shipped = 0;
        int done = 0;
        for (OrderLine item : bundle.getItems()) {
            Shipment shipment = item.getShipment();
            if (shipment == null || "CANCELLED".equals(shipment.getStatus()) || "PENDING".equals(shipment.getStatus())) {
                continue;
            }
            shipped += 1;
            if ("DONE".equals(shipment.getStatus())) {
                done += 1;
            }
        }
        return new Progress(shipped, done, bundle.getItems().size());
    }

    public record Progress(int shipped, int done, int total) {
    }

    public static void adjustPrice(OrderBundle bundle, OrderRequests.AdjustPrice req, String operator, Ids ids) {
        if (req == null || req.getReason() == null || req.getReason().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写改价原因");
        }
        assertOpen(bundle.getOrder(), "改价");
        SalesOrder order = bundle.getOrder();
        long previous = nz(order.getPayableAmount());
        String now = DateTimes.nowText();
        String reason = req.getReason().trim();
        if (req.getLines() != null) {
            for (OrderRequests.AdjustLine line : req.getLines()) {
                OrderLine item = findItem(bundle, line.getOrderItemId());
                if (line.getUnitPrice() != null) {
                    if (line.getUnitPrice() < 0) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "单价不能为负");
                    }
                    if (!line.getUnitPrice().equals(item.getUnitPrice())) {
                        addLog(bundle, ids, now, "LINE", item.getId(), "unitPrice", item.getUnitPrice(), line.getUnitPrice(), reason, operator);
                        item.setUnitPrice(line.getUnitPrice());
                        item.setLineAmount(line.getUnitPrice() * nzInt(item.getQuantity()));
                    }
                }
                if (line.getInstallFee() != null) {
                    if (line.getInstallFee() < 0) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "安装费不能为负");
                    }
                    if (!"ON_SITE_INSTALL".equals(item.getDeliveryMethod()) && line.getInstallFee() > 0) {
                        throw new BusinessException(ErrorCode.AMOUNT_MISMATCH, "仅上门安装行可设置安装费");
                    }
                    if (!Objects.equals(line.getInstallFee(), item.getInstallFee())) {
                        addLog(bundle, ids, now, "LINE", item.getId(), "installFee", item.getInstallFee(), line.getInstallFee(), reason, operator);
                        item.setInstallFee(line.getInstallFee());
                    }
                }
            }
        }
        if (req.getAdjustAmount() != null && !req.getAdjustAmount().equals(nz(order.getAdjustAmount()))) {
            addLog(bundle, ids, now, "ORDER", null, "adjustAmount", order.getAdjustAmount(), req.getAdjustAmount(), reason, operator);
            order.setAdjustAmount(req.getAdjustAmount());
        }
        recalc(bundle);
        if (nz(order.getPayableAmount()) < 0) {
            throw new BusinessException(ErrorCode.AMOUNT_MISMATCH, "应付金额不能为负");
        }
        String message = realignWaits(bundle, previous, now);
        if (message != null) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, message);
        }
        syncStatus(bundle);
    }

    public static void setFreight(OrderBundle bundle, long freightAmount, String operator, Ids ids) {
        if (freightAmount < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "运费不能为负");
        }
        SalesOrder order = bundle.getOrder();
        assertOpen(order, "修改运费");
        if (freightAmount == nz(order.getFreightAmount())) {
            return;
        }
        long previous = nz(order.getPayableAmount());
        String now = DateTimes.nowText();
        addLog(bundle, ids, now, "ORDER", null, "freightAmount", order.getFreightAmount(), freightAmount, "管理员手加运费", operator);
        order.setFreightAmount(freightAmount);
        recalc(bundle);
        long paid = paidAmount(bundle);
        if (nz(order.getPayableAmount()) < paid) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已付金额超过新应付，退差规则未定（O1），未改运费");
        }
        List<PaymentBill> waits = waits(bundle);
        if (waits.isEmpty()) {
            long gap = nz(order.getPayableAmount()) - paid;
            if (gap > 0) {
                int sortNo = maxSort(bundle) + 1;
                bundle.addPayment(buildPayment(bundle, ids, "FREIGHT", "运费", gap, sortNo, now));
            }
            syncStatus(bundle);
            return;
        }
        String message = realignWaits(bundle, previous, now);
        if (message != null) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, message);
        }
        syncStatus(bundle);
    }

    public static void createPayments(OrderBundle bundle, OrderRequests.CreatePayments req, Ids ids) {
        assertOpen(bundle.getOrder(), "调整应付");
        if (req == null || req.getPayments() == null || req.getPayments().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写应付阶段");
        }
        for (OrderRequests.PaymentDraft draft : req.getPayments()) {
            if (draft.getTitle() == null || draft.getTitle().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写应付标题");
            }
            if (draft.getAmount() == null || draft.getAmount() <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "应付金额须大于 0");
            }
        }
        String now = DateTimes.nowText();
        if ("SPLIT".equals(req.getMode())) {
            List<PaymentBill> fulls = bundle.getPayments().stream()
                    .filter(p -> "WAIT".equals(p.getStatus()) && "FULL".equals(p.getType()))
                    .toList();
            if (fulls.isEmpty()) {
                throw new BusinessException(ErrorCode.ILLEGAL_STATE, "没有可拆分的未付全款");
            }
            for (PaymentBill full : fulls) {
                full.setStatus("CLOSED");
                full.setUpdatedAt(DateTimes.now());
            }
        } else if (!"APPEND".equals(req.getMode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "mode 只能是 SPLIT 或 APPEND");
        }
        int sortNo = maxSort(bundle);
        for (OrderRequests.PaymentDraft draft : req.getPayments()) {
            sortNo += 1;
            PaymentBill bill = buildPayment(bundle, ids, draft.getType(), draft.getTitle().trim(), draft.getAmount(), sortNo, now);
            if (draft.getChannel() != null && !draft.getChannel().isBlank()) {
                bill.setChannel(draft.getChannel());
            }
            bundle.addPayment(bill);
        }
        syncStatus(bundle);
    }

    public static PaymentBill patchPayment(OrderBundle bundle, long paymentId, OrderRequests.PatchPayment req) {
        PaymentBill payment = findPayment(bundle, paymentId);
        if (!"WAIT".equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "只能修改未付应付单");
        }
        if (req.getAmount() != null) {
            if (req.getAmount() <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "应付金额须大于 0");
            }
            payment.setAmount(req.getAmount());
        }
        if (req.getTitle() != null) {
            if (req.getTitle().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标题不能为空");
            }
            payment.setTitle(req.getTitle().trim());
        }
        payment.setUpdatedAt(DateTimes.now());
        syncStatus(bundle);
        return payment;
    }

    /**
     * 登记线下到账。金额小于这一笔时，原笔按实收改成已收，差额变成新的待付。
     * 方式用银行转账、微信、支付宝、现金、其他；也接受 bank/wechat/alipay/cash/other 这几个短名。
     */
    public static PaymentBill confirmOffline(OrderBundle bundle, long paymentId, OrderRequests.OfflineConfirm req,
                                            long operatorId, String operatorName, Ids ids) {
        if ("CANCELLED".equals(bundle.getOrder().getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已取消订单不能登记收款");
        }
        PaymentBill payment = findPayment(bundle, paymentId);
        if (!"WAIT".equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "该应付单不是待付状态");
        }
        String method = normalizeMethod(req == null ? null : req.getMethod());
        if (method == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择收款方式");
        }
        long amount = req.getAmount() == null ? nz(payment.getAmount()) : req.getAmount();
        String amountError = offlineAmountError(amount, nz(payment.getAmount()));
        if (amountError != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, amountError);
        }
        String now = DateTimes.nowText();
        String paidAt = req.getPaidAt() == null || req.getPaidAt().isBlank() ? now : req.getPaidAt().trim();
        if (!paidAt.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "收款时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
        if (paidAt.compareTo(now) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "收款时间不能晚于当前时间");
        }
        long remaining = nz(payment.getAmount()) - amount;
        if (remaining > 0) {
            int sortNo = maxSort(bundle) + 1;
            PaymentBill rest = buildPayment(bundle, ids, payment.getType(), splitTitle(payment.getTitle()), remaining, sortNo, now);
            rest.setSplitFromId(payment.getId());
            bundle.addPayment(rest);
        }
        String remark = req.getRemark() == null ? "" : req.getRemark().trim();
        payment.setAmount(amount);
        payment.setStatus("SUCCESS");
        payment.setChannel("OFFLINE");
        payment.setOfflineMethod(method);
        payment.setVoucherUrls(Jsons.writeStrings(clean(req.getVoucherUrls())));
        payment.setRemark(remark.isEmpty() ? null : remark);
        payment.setPaidAt(LocalDateTime.parse(paidAt, DateTimes.FMT));
        payment.setOperatorId(operatorId);
        payment.setOperatorName(operatorName);
        payment.setRegisteredAt(LocalDateTime.parse(now, DateTimes.FMT));
        payment.setUpdatedAt(payment.getRegisteredAt());
        syncStatus(bundle);
        return payment;
    }

    /** 按行发货。不看有没有付款。快递要公司名和单号。 */
    public static Shipment addShipment(OrderBundle bundle, OrderRequests.ShipmentCreate req, Ids ids) {
        assertFulfillable(bundle.getOrder(), "发货");
        OrderLine item = findItem(bundle, req.getOrderItemId());
        if (req.getMethod() != null && !req.getMethod().isBlank() && !req.getMethod().equals(item.getDeliveryMethod())) {
            throw new BusinessException(ErrorCode.AMOUNT_MISMATCH, "发货方式与订单行不一致");
        }
        if (hasActiveShipment(item)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "该行已有发货记录");
        }
        assertVoucher(item.getDeliveryMethod(), req);
        String now = DateTimes.nowText();
        long id = ids.next();
        Shipment shipment = new Shipment();
        shipment.setId(id);
        shipment.setOrderId(bundle.getOrder().getId());
        shipment.setOrderItemId(item.getId());
        shipment.setMethod(item.getDeliveryMethod());
        shipment.setStatus("DISPATCHED");
        shipment.setCarrier(blankToNull(req.getCarrier()));
        shipment.setTrackingNo(blankToNull(req.getTrackingNo()));
        shipment.setLogisticsCompany(blankToNull(req.getLogisticsCompany()));
        shipment.setFreightNo(blankToNull(req.getFreightNo()));
        shipment.setVehicleNo(blankToNull(req.getVehicleNo()));
        shipment.setCourierName(blankToNull(req.getCourierName()));
        shipment.setCourierMobile(blankToNull(req.getCourierMobile()));
        shipment.setAppointAt(blankToNull(req.getAppointAt()));
        String pickup = blankToNull(req.getPickupCode());
        if (pickup == null && "STORE_PICKUP".equals(item.getDeliveryMethod())) {
            String text = String.valueOf(id);
            pickup = "ZT" + text.substring(Math.max(0, text.length() - 6));
        }
        shipment.setPickupCode(pickup);
        shipment.setPickupStoreId(req.getPickupStoreId());
        shipment.setRemark(blankToNull(req.getRemark()));
        shipment.setCreatedAt(DateTimes.now());
        shipment.setUpdatedAt(DateTimes.now());
        item.setShipment(shipment);
        item.setShipmentId(id);
        bundle.getFreshShipments().add(shipment);
        syncStatus(bundle);
        return shipment;
    }

    public static void cancel(OrderBundle bundle, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写取消原因");
        }
        SalesOrder order = bundle.getOrder();
        if ("CANCELLED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "订单已取消");
        }
        if ("COMPLETED".equals(order.getStatus()) || "FULFILLING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "履约中或已完成的订单不能直接取消");
        }
        if (bundle.getItems().stream().anyMatch(OrderRules::hasActiveShipment)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已有发货或派工记录，不能直接取消");
        }
        if (bundle.getPayments().stream().anyMatch(p -> "SUCCESS".equals(p.getStatus()))) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已有成功付款，取消需走退款（二期）");
        }
        LocalDateTime now = DateTimes.now();
        for (PaymentBill payment : bundle.getPayments()) {
            if ("WAIT".equals(payment.getStatus())) {
                payment.setStatus("CLOSED");
                payment.setUpdatedAt(now);
            }
        }
        order.setStatus("CANCELLED");
        String note = joinRemark(order.getAdminRemark(), "取消：" + reason.trim());
        order.setAdminRemark(note);
        syncStatus(bundle);
        order.setStatus("CANCELLED");
        mirrorLegacy(order);
    }

    public static PaymentBill buildPayment(OrderBundle bundle, Ids ids, String type, String title, long amount, int sortNo, String nowText) {
        SalesOrder order = bundle.getOrder();
        PaymentBill bill = new PaymentBill();
        long id = ids.next();
        String suffix = switch (type == null ? "" : type) {
            case "FULL" -> "F";
            case "DEPOSIT" -> "D";
            case "BALANCE" -> "B";
            case "REPAIR" -> "R";
            case "FREIGHT" -> "FR";
            default -> "X";
        };
        String no = order.getOrderNo() + "-" + suffix + "-" + sortNo;
        bill.setId(id);
        bill.setTenantId(1L);
        bill.setOrderId(order.getId());
        bill.setBizType("ORDER");
        bill.setBizId(order.getId());
        bill.setPaymentNo(no);
        bill.setOutTradeNo(no);
        bill.setChannel("OFFLINE");
        bill.setType(type);
        bill.setTitle(title);
        bill.setAmount(amount);
        bill.setStatus("WAIT");
        bill.setSortNo(sortNo);
        LocalDateTime now = LocalDateTime.parse(nowText, DateTimes.FMT);
        bill.setCreatedAt(now);
        bill.setUpdatedAt(now);
        return bill;
    }

    public static String splitTitle(String title) {
        String base = title == null ? "" : title.replace(SPLIT_SUFFIX, "");
        return base + SPLIT_SUFFIX;
    }

    public static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        String text = method.trim();
        String upper = text.toUpperCase();
        if (OFFLINE_METHODS.contains(upper)) {
            return upper;
        }
        return switch (text.toLowerCase()) {
            case "bank" -> "BANK_TRANSFER";
            case "wechat" -> "WECHAT_TRANSFER";
            case "alipay" -> "ALIPAY";
            case "cash" -> "CASH";
            case "other" -> "OTHER";
            default -> null;
        };
    }

    private static String offlineAmountError(long amount, long remaining) {
        if (amount <= 0) {
            return "本次收款金额须大于 0";
        }
        if (amount > remaining) {
            return "本次收款金额不能超过剩余 ¥" + yuan(remaining);
        }
        return null;
    }

    private static String yuan(long fen) {
        if (fen % 100 == 0) {
            return Long.toString(fen / 100);
        }
        return String.format("%.2f", fen / 100.0);
    }

    private static void assertVoucher(String method, OrderRequests.ShipmentCreate req) {
        if ("EXPRESS".equals(method)) {
            requireText(req.getCarrier(), "承运商");
            requireText(req.getTrackingNo(), "快递单号");
        } else if ("FREIGHT".equals(method)) {
            requireText(req.getLogisticsCompany(), "物流公司");
            if (blank(req.getFreightNo()) && blank(req.getVehicleNo())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写物流单号或车牌");
            }
        } else if ("HOME_DELIVERY".equals(method)) {
            requireText(req.getCourierName(), "送货人");
            requireText(req.getCourierMobile(), "送货人电话");
        } else if ("ON_SITE_INSTALL".equals(method)) {
            requireText(req.getCourierName(), "师傅姓名");
        }
    }

    private static String realignWaits(OrderBundle bundle, long previousPayable, String now) {
        SalesOrder order = bundle.getOrder();
        long paid = paidAmount(bundle);
        if (nz(order.getPayableAmount()) < paid) {
            return "已付金额超过新应付，退差规则未定（O1），未改价";
        }
        long delta = nz(order.getPayableAmount()) - previousPayable;
        if (delta == 0) {
            return null;
        }
        List<PaymentBill> waitList = waits(bundle);
        if (waitList.isEmpty()) {
            if (delta > 0) {
                return "没有未付应付单可调整，请先追加一笔待付";
            }
            return null;
        }
        LocalDateTime updated = LocalDateTime.parse(now, DateTimes.FMT);
        if (waitList.size() == 1) {
            PaymentBill wait = waitList.get(0);
            long unpaid = nz(order.getPayableAmount()) - paid;
            if (unpaid <= 0) {
                wait.setStatus("CLOSED");
            } else {
                wait.setAmount(unpaid);
            }
            wait.setUpdatedAt(updated);
            return null;
        }
        PaymentBill last = waitList.get(waitList.size() - 1);
        long nextAmount = nz(last.getAmount()) + delta;
        if (nextAmount <= 0) {
            return "存在多笔未付，自动分摊后金额不为正，请手工修改各笔应付";
        }
        last.setAmount(nextAmount);
        last.setUpdatedAt(updated);
        return null;
    }

    private static void addLog(OrderBundle bundle, Ids ids, String now, String scope, Long itemId, String field,
                               Long before, long after, String reason, String operator) {
        PriceLog log = new PriceLog();
        log.setId(ids.next());
        log.setOrderId(bundle.getOrder().getId());
        log.setScope(scope);
        log.setOrderItemId(itemId);
        log.setFieldName(field);
        log.setBeforeAmount(before);
        log.setAfterAmount(after);
        log.setReason(reason);
        log.setOperatorName(operator);
        log.setCreatedAt(LocalDateTime.parse(now, DateTimes.FMT));
        bundle.addLog(log);
        bundle.getOrder().setPriceAdjusted(1);
    }

    private static boolean fulfillmentStarted(OrderBundle bundle) {
        for (OrderLine item : bundle.getItems()) {
            Shipment shipment = item.getShipment();
            if (shipment != null && !"CANCELLED".equals(shipment.getStatus()) && !"PENDING".equals(shipment.getStatus())) {
                return true;
            }
            if ("ON_SITE_INSTALL".equals(item.getDeliveryMethod())
                    && item.getTechnicianId() != null
                    && item.getWorkOrderNo() != null
                    && !item.getWorkOrderNo().isBlank()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasActiveShipment(OrderLine item) {
        return item.getShipment() != null && !"CANCELLED".equals(item.getShipment().getStatus());
    }

    private static void assertOpen(SalesOrder order, String action) {
        if ("CANCELLED".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) {
            String word = "CANCELLED".equals(order.getStatus()) ? "取消" : "完成";
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "订单已" + word + "，不能" + action);
        }
    }

    public static void assertFulfillable(SalesOrder order, String action) {
        if ("CANCELLED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已取消订单不能" + action);
        }
        if ("COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已完成订单不能" + action);
        }
    }

    private static OrderLine findItem(OrderBundle bundle, Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单行不存在");
        }
        return bundle.getItems().stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单行不存在"));
    }

    private static PaymentBill findPayment(OrderBundle bundle, long id) {
        return bundle.getPayments().stream()
                .filter(item -> item.getId() != null && item.getId() == id)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应付单不存在"));
    }

    private static List<PaymentBill> waits(OrderBundle bundle) {
        return bundle.getPayments().stream().filter(p -> "WAIT".equals(p.getStatus())).toList();
    }

    private static long paidAmount(OrderBundle bundle) {
        return bundle.getPayments().stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()))
                .mapToLong(p -> nz(p.getAmount()))
                .sum();
    }

    private static int maxSort(OrderBundle bundle) {
        return bundle.getPayments().stream().map(PaymentBill::getSortNo).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
    }

    private static void mirrorLegacy(SalesOrder order) {
        long payable = nz(order.getPayableAmount());
        long paid = nz(order.getPaidAmount());
        order.setTotalAmount(payable);
        order.setDepositAmount(order.getDepositAmount() == null ? 0L : order.getDepositAmount());
        order.setBalanceAmount(Math.max(0, payable - paid));
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写" + label);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String joinRemark(String origin, String extra) {
        if (origin == null || origin.isBlank()) {
            return extra;
        }
        return origin + "\n" + extra;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static int nzInt(Integer value) {
        return value == null ? 0 : value;
    }
}
