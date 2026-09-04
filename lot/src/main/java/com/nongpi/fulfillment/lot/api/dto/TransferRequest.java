package com.nongpi.fulfillment.lot.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 批次转库请求 DTO
 */
public record TransferRequest(
        @NotBlank(message = "批次号不能为空") String lotNo,
        @NotNull(message = "数量不能为空") @DecimalMin(value = "0.01", message = "数量必须大于0") BigDecimal qty,
        String fromLocation,
        String toLocation
) {}
