package com.nongpi.fulfillment.lot.api.dto;

import com.nongpi.fulfillment.common.domain.TempZone;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 批次出库请求 DTO
 *
 * <p>lotNo 可选：传入=指定批次直接出库，不传=FEFO 自动选最早过期批次。</p>
 */
public record OutboundRequest(
        @NotNull(message = "skuId不能为空") Long skuId,
        @NotNull(message = "tempZone不能为空") TempZone tempZone,
        @NotNull(message = "数量不能为空") @DecimalMin(value = "0.01", message = "数量必须大于0") BigDecimal qty,
        String toLocation,
        String lotNo
) {}
