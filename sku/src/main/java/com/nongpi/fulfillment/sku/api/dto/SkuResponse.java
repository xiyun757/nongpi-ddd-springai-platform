package com.nongpi.fulfillment.sku.api.dto;

import com.nongpi.fulfillment.sku.domain.Sku;

import java.time.LocalDateTime;

/**
 * 商品对外响应 DTO
 */
public record SkuResponse(
        Long id,
        String name,
        String spec,
        String unit,
        String barcode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SkuResponse fromDomain(Sku sku) {
        return new SkuResponse(
                sku.getId(),
                sku.getName(),
                sku.getSpec(),
                sku.getUnit(),
                sku.getBarcode(),
                null,
                null
        );
    }
}
