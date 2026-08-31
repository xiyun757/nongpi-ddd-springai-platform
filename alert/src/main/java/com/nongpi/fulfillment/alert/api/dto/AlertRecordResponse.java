package com.nongpi.fulfillment.alert.api.dto;

import com.nongpi.fulfillment.alert.infrastructure.persistence.AlertRecordPO;

import java.time.LocalDateTime;

/**
 * 预警记录对外响应 DTO（驼峰字段）。
 */
public record AlertRecordResponse(
        Long id,
        String lotNo,
        Long alertRuleId,
        String alertLevel,
        String message,
        boolean handled,
        String handler,
        LocalDateTime handledAt,
        LocalDateTime createdAt
) {
    public static AlertRecordResponse fromPO(AlertRecordPO po) {
        return new AlertRecordResponse(
                po.getId(),
                po.getLotNo(),
                po.getAlertRuleId(),
                po.getAlertLevel(),
                po.getMessage(),
                Boolean.TRUE.equals(po.getHandled()),
                po.getHandler(),
                po.getHandledAt(),
                po.getCreatedAt()
        );
    }
}
