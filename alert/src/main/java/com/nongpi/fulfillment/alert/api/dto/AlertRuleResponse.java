package com.nongpi.fulfillment.alert.api.dto;

/**
 * 预警规则响应 DTO — 独立于 Controller 的 API 层对象
 *
 * <p>由 {@code alert.application.AlertQueryService} 组装并返回，
 * 应用层不依赖 Controller 嵌套类型（DDD 分层倒置修复）。</p>
 *
 * @param id            规则 ID
 * @param skuId         商品 SKU ID（null 表示全局规则）
 * @param tempZone      温区（null 表示所有温区）
 * @param thresholdDays 过期警戒天数
 * @param alertLevel    预警级别：INFO / WARNING / CRITICAL
 * @param enabled       是否启用
 * @param createdAt     创建时间（ISO 字符串）
 */
public record AlertRuleResponse(
        Long id,
        Long skuId,
        String tempZone,
        int thresholdDays,
        String alertLevel,
        boolean enabled,
        String createdAt
) {}
