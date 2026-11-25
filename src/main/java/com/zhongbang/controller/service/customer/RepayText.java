package com.zhongbang.controller.service.customer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.pojo.customer.entity.RepayAgreement;
import com.zhongbang.controller.pojo.customer.vo.CustomerVo;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 协议在页面上怎么显示：关联的订单号、付款单标题、材料列表，以及它落在哪个提醒格子里。
 */
@Component
public class RepayText {

    private static final Set<String> KINDS = Set.of("IMAGE", "AUDIO", "DOCUMENT", "TEXT");
    private static final TypeReference<List<CustomerVo.Material>> MATERIALS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final SalesOrderMapper orderMapper;
    private final PaymentBillMapper paymentBillMapper;

    public RepayText(ObjectMapper objectMapper, SalesOrderMapper orderMapper, PaymentBillMapper paymentBillMapper) {
        this.objectMapper = objectMapper;
        this.orderMapper = orderMapper;
        this.paymentBillMapper = paymentBillMapper;
    }

    public CustomerVo.Agreement toVo(RepayAgreement row, String customerName) {
        String orderNo = null;
        if (row.getOrderId() != null) {
            SalesOrder order = orderMapper.selectById(row.getOrderId());
            orderNo = order == null ? null : order.getOrderNo();
        }
        String paymentTitle = null;
        if (row.getPaymentId() != null) {
            PaymentBill payment = paymentBillMapper.selectById(row.getPaymentId());
            paymentTitle = payment == null ? null : payment.getTitle();
        }
        return new CustomerVo.Agreement(
                row.getId(),
                row.getCustomerId(),
                customerName,
                row.getScope(),
                row.getOrderId(),
                orderNo,
                row.getPaymentId(),
                paymentTitle,
                row.getDueDate() == null ? null : row.getDueDate().toString(),
                row.getAgreedAmount() == null ? 0L : row.getAgreedAmount(),
                row.getStatus(),
                row.getNote(),
                readMaterials(row.getMaterialsJson()),
                row.getOperatorName(),
                DateTimes.format(row.getCreatedAt()),
                DateTimes.format(row.getUpdatedAt()),
                bucket(row, LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")), 3)
        );
    }

    /**
     * SOON 到期前若干天（不含今天），TODAY 今天，OVERDUE 已过期，NONE 不在提醒里。
     * days 来自配置，默认 3。
     */
    public String bucket(RepayAgreement row, LocalDate today, int days) {
        if (row.getDueDate() == null || !"ACTIVE".equals(row.getStatus())) {
            return "NONE";
        }
        if (row.getDueDate().isBefore(today)) {
            return "OVERDUE";
        }
        if (row.getDueDate().isEqual(today)) {
            return "TODAY";
        }
        int window = Math.max(days, 1);
        if (!row.getDueDate().isAfter(today.plusDays(window))) {
            return "SOON";
        }
        return "NONE";
    }

    public List<CustomerVo.Material> readMaterials(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<CustomerVo.Material> list = objectMapper.readValue(json, MATERIALS);
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public String writeMaterials(List<CustomerVo.Material> materials) {
        if (materials == null || materials.isEmpty()) {
            return null;
        }
        if (materials.size() > 9) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "一份协议最多 9 个材料");
        }
        List<CustomerVo.Material> clean = new ArrayList<>();
        for (CustomerVo.Material material : materials) {
            if (material == null) {
                continue;
            }
            String kind = material.kind() == null ? "" : material.kind().trim().toUpperCase();
            if (!KINDS.contains(kind)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "材料类型只能是图片、录音、文档或文字");
            }
            if ("TEXT".equals(kind)) {
                String text = material.text() == null ? "" : material.text().trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (text.length() > 500) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "文字说明不能超过 500 字");
                }
                clean.add(new CustomerVo.Material("TEXT", null, null, "text/plain", text));
                continue;
            }
            String url = material.url() == null ? "" : material.url().trim();
            if (url.isEmpty() || url.length() > 500) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请先上传材料");
            }
            String name = material.name() == null ? "" : material.name().trim();
            if (name.length() > 200) {
                name = name.substring(0, 200);
            }
            clean.add(new CustomerVo.Material(kind, url, name, material.contentType(), null));
        }
        if (clean.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(clean);
            if (json.length() > 4000) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "材料太多，请减少附件");
            }
            return json;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "材料保存失败");
        }
    }

    public String snapshot(RepayAgreement row) {
        try {
            String json = objectMapper.writeValueAsString(toVo(row, null));
            return json.length() > 4000 ? json.substring(0, 4000) : json;
        } catch (Exception ex) {
            return "{}";
        }
    }
}
