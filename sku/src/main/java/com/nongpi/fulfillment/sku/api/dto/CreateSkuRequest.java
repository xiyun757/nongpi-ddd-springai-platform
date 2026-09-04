package com.nongpi.fulfillment.sku.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 商品创建请求 DTO
 */
public record CreateSkuRequest(
        @NotBlank(message = "商品名称不能为空") String name,
        String spec,
        @NotBlank(message = "计量单位不能为空") String unit,
        String barcode
) {}
