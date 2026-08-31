package com.nongpi.fulfillment.lot.api.dto;

import com.nongpi.fulfillment.lot.infrastructure.persistence.LotPO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次对外响应 DTO（驼峰字段）。
 * <p>避免直接暴露 LotPO（含 version 等持久化字段）给前端。</p>
 */
public record LotResponse(
        String lotNo,
        Long skuId,
        String tempZone,
        LocalDate produceDate,
        LocalDate expireDate,
        BigDecimal initialQty,
        BigDecimal remainingQty,
        String status,
        Long supplierId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LotResponse fromPO(LotPO po) {
        return new LotResponse(
                po.getLotNo(),
                po.getSkuId(),
                po.getTempZone(),
                po.getProduceDate(),
                po.getExpireDate(),
                po.getInitialQty(),
                po.getRemainingQty(),
                po.getStatus(),
                po.getSupplierId(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }
}
