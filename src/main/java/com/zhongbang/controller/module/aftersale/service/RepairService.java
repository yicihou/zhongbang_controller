package com.zhongbang.controller.module.aftersale.service;

import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.aftersale.dto.CreateRepairRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * P1c skeleton — tables/entities exist; business flow TODO.
 */
@Service
public class RepairService {

    public Map<String, Object> create(CreateRepairRequest request) {
        // TODO(P1c): create repair_ticket + lines, sync EXTERNAL assets, SSE REPAIR_CREATED
        throw new BusinessException(ErrorCode.BAD_REQUEST, "报修提交尚未实现（P1c）");
    }

    public PageResult<Map<String, Object>> listMine(int page, int size) {
        // TODO(P1c)
        return PageResult.of(List.of(), 0, PageResult.normalizePage(page), PageResult.normalizeSize(size));
    }

    public Map<String, Object> detailMine(Long id) {
        // TODO(P1c)
        throw new BusinessException(ErrorCode.NOT_FOUND, "报修单不存在或尚未实现（P1c）");
    }

    public Map<String, Object> confirmQuote(Long id) {
        // TODO(P1c): customer confirms quote → PENDING_PAYMENT or CONFIRMED
        throw new BusinessException(ErrorCode.BAD_REQUEST, "报价确认尚未实现（P1c）");
    }

    public PageResult<Map<String, Object>> adminList(int page, int size) {
        // TODO(P1c)
        return PageResult.of(List.of(), 0, PageResult.normalizePage(page), PageResult.normalizeSize(size));
    }

    public Map<String, Object> adminDetail(Long id) {
        // TODO(P1c)
        throw new BusinessException(ErrorCode.NOT_FOUND, "报修单不存在或尚未实现（P1c）");
    }

    public Map<String, Object> quote(Long id, Map<String, Object> body) {
        // TODO(P1c)
        throw new BusinessException(ErrorCode.BAD_REQUEST, "报修报价尚未实现（P1c）");
    }

    public Map<String, Object> assign(Long id, Map<String, Object> body) {
        // TODO(P1c)
        throw new BusinessException(ErrorCode.BAD_REQUEST, "报修派工尚未实现（P1c）");
    }
}
