package com.nongpi.fulfillment.lot.api.dto;

import com.nongpi.fulfillment.common.domain.TempZone;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 批次入库请求 DTO
 */
public record InboundRequest(
        @NotNull(message = "skuId不能为空") Long skuId,
        @NotNull(message = "tempZone不能为空") TempZone tempZone,
        @NotNull(message = "生产日期不能为空") LocalDate produceDate,
        @NotNull(message = "过期日期不能为空") LocalDate expireDate,
        @NotNull(message = "数量不能为空") @DecimalMin(value = "0.01", message = "数量必须大于0") BigDecimal qty,
        @NotNull(message = "supplierId不能为空") Long supplierId
) {}
