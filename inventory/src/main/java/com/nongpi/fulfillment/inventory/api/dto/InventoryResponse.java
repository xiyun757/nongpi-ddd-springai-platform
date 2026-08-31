package com.nongpi.fulfillment.inventory.api.dto;

import com.nongpi.fulfillment.inventory.infrastructure.persistence.InventoryPO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存对外响应 DTO（驼峰字段）。
 */
public record InventoryResponse(
        Long id,
        Long skuId,
        String tempZone,
        BigDecimal totalQty,
        BigDecimal frozenQty,
        BigDecimal availableQty,
        LocalDateTime updatedAt
) {
    public static InventoryResponse fromPO(InventoryPO po) {
        BigDecimal available = po.getTotalQty().subtract(po.getFrozenQty());
        return new InventoryResponse(
                po.getId(),
                po.getSkuId(),
                po.getTempZone(),
                po.getTotalQty(),
                po.getFrozenQty(),
                available,
                po.getUpdatedAt()
        );
    }
}
