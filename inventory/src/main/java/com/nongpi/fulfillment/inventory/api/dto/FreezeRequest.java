package com.nongpi.fulfillment.inventory.api.dto;

import com.nongpi.fulfillment.common.domain.TempZone;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 库存冻结/解冻请求 DTO
 */
public record FreezeRequest(
        @NotNull(message = "skuId不能为空") Long skuId,
        @NotNull(message = "tempZone不能为空") TempZone tempZone,
        @NotNull(message = "数量不能为空") @DecimalMin(value = "0.01", message = "数量必须大于0") BigDecimal qty
) {}
