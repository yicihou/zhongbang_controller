package com.zhongbang.controller.pojo.repair.vo;

import com.zhongbang.controller.pojo.order.vo.OrderVo;

import java.util.List;

/**
 * 返回给页面的报修单。
 * 管理端字段对齐 admin-web 的 {@code RepairTicket}。
 * 小程序详情多了状态中文、进度和保修说明，和原来的 Mock 页面兼容。
 */
public final class RepairVo {

    private RepairVo() {
    }

    public record Admin(
            Long id,
            String ticketNo,
            Long customerId,
            String customerName,
            String status,
            String expectDate,
            String expectSlot,
            String contactMobile,
            String remark,
            Long quoteAmount,
            long payableAmount,
            long paidAmount,
            Boolean inWarranty,
            String createdAt,
            String updatedAt,
            List<Line> lines,
            List<OrderVo.Payment> payments
    ) {
    }

    public record Line(
            Long id,
            Long ticketId,
            int lineNo,
            Long assetId,
            String brand,
            String model,
            String sn,
            String source,
            String faultType,
            String faultDesc,
            List<String> images,
            String purchaseProofUrl,
            String purchaseChannel,
            Long lineQuoteAmount,
            Long workOrderId,
            Long technicianId,
            String technicianName,
            String appointAt
    ) {
    }

    /** 小程序提交成功。ticketId 和 id 是同一个数，页面两种写法都能跳到详情。 */
    public record Created(
            Long ticketId,
            Long id,
            String ticketNo,
            String status,
            String statusText,
            int lineCount
    ) {
    }

    /** 小程序列表里的一行，不带全部明细。 */
    public record MineItem(
            Long id,
            String ticketNo,
            String status,
            String statusText,
            Long quoteAmount,
            int lineCount,
            String createdAt,
            String expectDate,
            String faultText
    ) {
    }

    public record Mine(
            Long id,
            String ticketNo,
            String workNo,
            String status,
            String statusText,
            String typeText,
            String expectDate,
            String expectSlot,
            String faultText,
            boolean inWarranty,
            String warrantyDesc,
            Long quoteAmount,
            long payableAmount,
            long paidAmount,
            String createdAt,
            List<Line> lines,
            List<QuoteItem> quoteItems,
            List<OrderVo.Payment> payments,
            List<Timeline> timeline
    ) {
    }

    public record QuoteItem(String name, long amount) {
    }

    public record Timeline(
            String status,
            String title,
            String desc,
            String time,
            boolean done,
            boolean current
    ) {
    }

    /** 客户确认报价后，页面只看 status。 */
    public record Status(String status, String statusText) {
    }
}
