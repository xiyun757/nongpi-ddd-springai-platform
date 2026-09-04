package com.nongpi.fulfillment.alert.api.dto;

import com.nongpi.fulfillment.common.domain.AlertLevel;
import com.nongpi.fulfillment.common.domain.TempZone;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 创建预警规则请求 DTO
 */
public record CreateRuleRequest(
        Long skuId,
        TempZone tempZone,
        @NotNull(message = "阈值天数不能为空") @Min(value = 1, message = "阈值天数必须大于0") int thresholdDays,
        @NotNull(message = "预警级别不能为空") AlertLevel alertLevel
) {}
