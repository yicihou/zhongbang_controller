package com.zhongbang.controller.service.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.config.ZhongbangProperties;
import com.zhongbang.controller.mapper.customer.CustomerMapper;
import com.zhongbang.controller.mapper.customer.RepayAgreementLogMapper;
import com.zhongbang.controller.mapper.customer.RepayAgreementMapper;
import com.zhongbang.controller.mapper.customer.RepayRemindLogMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.pojo.customer.dto.CustomerRequests;
import com.zhongbang.controller.pojo.customer.entity.Customer;
import com.zhongbang.controller.pojo.customer.entity.RepayAgreement;
import com.zhongbang.controller.pojo.customer.entity.RepayAgreementLog;
import com.zhongbang.controller.pojo.customer.entity.RepayRemindLog;
import com.zhongbang.controller.pojo.customer.vo.CustomerVo;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import com.zhongbang.controller.service.notice.NoticeService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 协议还款：可以挂在整个客户、某一张订单或某一笔付款单（含余款）上。
 * 修改、履约、作废都留历史。定时任务每天检查即将到期、今天到期和已逾期，只通知老板。
 */
@Service
public class RepayAgreementService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SCOPES = Set.of("CUSTOMER", "ORDER", "PAYMENT");

    private final RepayAgreementMapper agreementMapper;
    private final RepayAgreementLogMapper logMapper;
    private final RepayRemindLogMapper remindLogMapper;
    private final CustomerMapper customerMapper;
    private final SalesOrderMapper orderMapper;
    private final PaymentBillMapper paymentBillMapper;
    private final RepairTicketMapper repairTicketMapper;
    private final RepayText repayText;
    private final NoticeService noticeService;
    private final ZhongbangProperties properties;
    private final CustomerQueryService queryService;

    public RepayAgreementService(
            RepayAgreementMapper agreementMapper,
            RepayAgreementLogMapper logMapper,
            RepayRemindLogMapper remindLogMapper,
            CustomerMapper customerMapper,
            SalesOrderMapper orderMapper,
            PaymentBillMapper paymentBillMapper,
            RepairTicketMapper repairTicketMapper,
            RepayText repayText,
            NoticeService noticeService,
            ZhongbangProperties properties,
            CustomerQueryService queryService
    ) {
        this.agreementMapper = agreementMapper;
        this.logMapper = logMapper;
        this.remindLogMapper = remindLogMapper;
        this.customerMapper = customerMapper;
        this.orderMapper = orderMapper;
        this.paymentBillMapper = paymentBillMapper;
        this.repairTicketMapper = repairTicketMapper;
        this.repayText = repayText;
        this.noticeService = noticeService;
        this.properties = properties;
        this.queryService = queryService;
    }

    public PageResult<CustomerVo.Agreement> reminders(String bucket, int page, int size) {
        StaffRoles.requireBoss();
        String wanted = bucket == null || bucket.isBlank() ? "OVERDUE" : bucket.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SOON", "TODAY", "OVERDUE").contains(wanted)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提醒分类只能是即将到期、今天到期或已逾期");
        }
        LocalDate today = LocalDate.now(SHANGHAI);
        int days = Math.max(properties.getCustomer().getRemindDays(), 1);
        List<CustomerVo.Agreement> rows = agreementMapper.selectList(new LambdaQueryWrapper<RepayAgreement>()
                        .eq(RepayAgreement::getStatus, "ACTIVE")
                        .orderByAsc(RepayAgreement::getDueDate))
                .stream()
                .filter(row -> wanted.equals(repayText.bucket(row, today, days)))
                .map(row -> repayText.toVo(row, customerName(row.getCustomerId())))
                .toList();
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        int from = Math.min((p - 1) * s, rows.size());
        int to = Math.min(from + s, rows.size());
        return PageResult.of(rows.subList(from, to), rows.size(), p, s);
    }

    @Transactional
    public CustomerVo.Detail create(Long customerId, CustomerRequests.Agreement request) {
        StaffRoles.requireBoss();
        Customer customer = queryService.requireCustomer(customerId);
        RepayAgreement row = new RepayAgreement();
        row.setCustomerId(customer.getId());
        row.setStatus("ACTIVE");
        row.setCreatedAt(DateTimes.now());
        fill(row, customer.getId(), request);
        row.setOperatorName(UserContext.require().getDisplayName());
        row.setUpdatedAt(DateTimes.now());
        agreementMapper.insert(row);
        log(row, "CREATE");
        return queryService.detail(customerId);
    }

    @Transactional
    public CustomerVo.Agreement update(Long id, CustomerRequests.Agreement request) {
        StaffRoles.requireBoss();
        RepayAgreement row = requireActive(id);
        fill(row, row.getCustomerId(), request);
        row.setOperatorName(UserContext.require().getDisplayName());
        row.setUpdatedAt(DateTimes.now());
        agreementMapper.updateById(row);
        log(row, "UPDATE");
        return repayText.toVo(row, customerName(row.getCustomerId()));
    }

    @Transactional
    public CustomerVo.Agreement fulfill(Long id) {
        return changeStatus(id, "FULFILLED", "FULFILL");
    }

    @Transactional
    public CustomerVo.Agreement voidAgreement(Long id) {
        return changeStatus(id, "VOID", "VOID");
    }

    public List<CustomerVo.AgreementLog> history(Long id) {
        StaffRoles.requireBoss();
        require(id);
        return logMapper.selectList(new LambdaQueryWrapper<RepayAgreementLog>()
                        .eq(RepayAgreementLog::getAgreementId, id)
                        .orderByDesc(RepayAgreementLog::getCreatedAt))
                .stream()
                .map(row -> new CustomerVo.AgreementLog(
                        row.getId(),
                        row.getAction(),
                        row.getSnapshotJson(),
                        row.getOperatorName(),
                        DateTimes.format(row.getCreatedAt())
                ))
                .toList();
    }

    /**
     * 给定时任务和页面上的「立即检查」用。返回这次新写出的提醒条数。
     * 同一协议同一天同一种提醒只会写一次。
     */
    @Transactional
    public int scan(LocalDate today) {
        LocalDate day = today == null ? LocalDate.now(SHANGHAI) : today;
        int days = Math.max(properties.getCustomer().getRemindDays(), 1);
        int created = 0;
        List<RepayAgreement> active = agreementMapper.selectList(new LambdaQueryWrapper<RepayAgreement>()
                .eq(RepayAgreement::getStatus, "ACTIVE"));
        for (RepayAgreement row : active) {
            String kind = repayText.bucket(row, day, days);
            if ("NONE".equals(kind)) {
                continue;
            }
            if (!markReminded(row.getId(), day, kind)) {
                continue;
            }
            String name = customerName(row.getCustomerId());
            noticeService.bossNotice(title(kind), body(name, row), "CUSTOMER", row.getCustomerId(), "REPAY_" + kind);
            created++;
        }
        return created;
    }

    private CustomerVo.Agreement changeStatus(Long id, String status, String action) {
        StaffRoles.requireBoss();
        RepayAgreement row = requireActive(id);
        row.setStatus(status);
        row.setOperatorName(UserContext.require().getDisplayName());
        row.setUpdatedAt(DateTimes.now());
        agreementMapper.updateById(row);
        log(row, action);
        return repayText.toVo(row, customerName(row.getCustomerId()));
    }

    private void fill(RepayAgreement row, Long customerId, CustomerRequests.Agreement request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写协议");
        }
        String scope = request.scope() == null ? "CUSTOMER" : request.scope().trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "协议范围只能是整个客户、某张订单或某笔付款");
        }
        if (request.agreedAmount() == null || request.agreedAmount() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "约定金额要大于 0");
        }
        if (request.dueDate() == null || request.dueDate().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写约定还款日");
        }
        LocalDate due;
        try {
            due = LocalDate.parse(request.dueDate().trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "约定还款日请写成 yyyy-MM-dd");
        }
        Long orderId = null;
        Long paymentId = null;
        if ("ORDER".equals(scope)) {
            SalesOrder order = requireOrder(customerId, request.orderId());
            orderId = order.getId();
        } else if ("PAYMENT".equals(scope)) {
            PaymentBill payment = requirePayment(customerId, request.paymentId());
            paymentId = payment.getId();
            orderId = "REPAIR".equals(payment.getBizType()) ? null : payment.getBizId() != null ? payment.getBizId() : payment.getOrderId();
        }
        String note = request.note() == null ? null : request.note().trim();
        if (note != null && note.length() > 1000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "说明不能超过 1000 字");
        }
        row.setScope(scope);
        row.setOrderId(orderId);
        row.setPaymentId(paymentId);
        row.setDueDate(due);
        row.setAgreedAmount(request.agreedAmount());
        row.setNote(note == null || note.isEmpty() ? null : note);
        row.setMaterialsJson(repayText.writeMaterials(request.materials()));
    }

    private SalesOrder requireOrder(Long customerId, Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择订单");
        }
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || !customerId.equals(order.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在或不属于这个客户");
        }
        return order;
    }

    private PaymentBill requirePayment(Long customerId, Long paymentId) {
        if (paymentId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择付款单");
        }
        PaymentBill payment = paymentBillMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "付款单不存在");
        }
        if ("REPAIR".equals(payment.getBizType())) {
            RepairTicket ticket = repairTicketMapper.selectById(payment.getBizId() != null ? payment.getBizId() : payment.getOrderId());
            if (ticket == null || !customerId.equals(ticket.getCustomerId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "付款单不存在或不属于这个客户");
            }
            return payment;
        }
        Long orderId = payment.getBizId() != null ? payment.getBizId() : payment.getOrderId();
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || !customerId.equals(order.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "付款单不存在或不属于这个客户");
        }
        return payment;
    }

    private RepayAgreement requireActive(Long id) {
        RepayAgreement row = require(id);
        if (!"ACTIVE".equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "这份协议已经履约或作废，不能再改");
        }
        return row;
    }

    private RepayAgreement require(Long id) {
        RepayAgreement row = agreementMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "协议不存在");
        }
        return row;
    }

    private void log(RepayAgreement row, String action) {
        RepayAgreementLog log = new RepayAgreementLog();
        log.setAgreementId(row.getId());
        log.setAction(action);
        log.setSnapshotJson(repayText.snapshot(row));
        log.setOperatorName(row.getOperatorName());
        log.setCreatedAt(DateTimes.now());
        logMapper.insert(log);
    }

    private boolean markReminded(Long agreementId, LocalDate day, String kind) {
        RepayRemindLog log = new RepayRemindLog();
        log.setAgreementId(agreementId);
        log.setRemindOn(day.toString());
        log.setKind(kind);
        log.setCreatedAt(DateTimes.now());
        try {
            remindLogMapper.insert(log);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private String customerName(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null || customer.getName() == null || customer.getName().isBlank()) {
            return "客户";
        }
        return customer.getName();
    }

    private static String title(String kind) {
        return switch (kind) {
            case "TODAY" -> "协议还款今天到期";
            case "OVERDUE" -> "协议还款已逾期";
            default -> "协议还款即将到期";
        };
    }

    private static String body(String name, RepayAgreement row) {
        String yuan = row.getAgreedAmount() == null ? "0" : (row.getAgreedAmount() % 100 == 0
                ? Long.toString(row.getAgreedAmount() / 100)
                : String.format("%.2f", row.getAgreedAmount() / 100.0));
        return name + " 约定 " + row.getDueDate() + " 还款 ¥" + yuan;
    }
}
