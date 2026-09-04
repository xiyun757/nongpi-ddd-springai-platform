package com.nongpi.fulfillment.alert.api.dto;

/**
 * 预警详情响应 DTO（含 id 字段，前端处理时需要）
 */
public record AlertRecordDetailResponse(
        Long id, String lotNo, Long alertRuleId, String alertLevel,
        String message, boolean handled, String handler,
        String handledAt, String createdAt
) {}
