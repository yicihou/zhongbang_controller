package com.zhongbang.controller.service.technician;

import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.pojo.technician.entity.Technician;

/**
 * 派工前对师傅做的共同检查。订单安装和报修按行派工都走这里，避免停用的人还能被选上。
 */
public final class TechnicianRules {

    private TechnicianRules() {
    }

    /** 找不到或已删除是 404；状态是 DISABLED 则不能派工。status 为空时当成启用，兼容旧数据。 */
    public static Technician requireAssignable(Technician technician) {
        if (technician == null || (technician.getDeleted() != null && technician.getDeleted() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "师傅不存在");
        }
        if ("DISABLED".equalsIgnoreCase(technician.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "师傅已停用，不能派工");
        }
        return technician;
    }

    public static boolean enabled(Technician technician) {
        if (technician == null || (technician.getDeleted() != null && technician.getDeleted() == 1)) {
            return false;
        }
        String status = technician.getStatus();
        return status == null || status.isBlank() || "ENABLED".equalsIgnoreCase(status);
    }
}
