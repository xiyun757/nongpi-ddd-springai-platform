package com.nongpi.fulfillment.inventory.api.dto;

import com.nongpi.fulfillment.common.domain.TempZone;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 库存调整请求 DTO
 */
public record AdjustRequest(
        @NotNull(message = "skuId不能为空") Long skuId,
        @NotNull(message = "tempZone不能为空") TempZone tempZone,
        @NotNull(message = "调整数量不能为空") BigDecimal delta
) {}
