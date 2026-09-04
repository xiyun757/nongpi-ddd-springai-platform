package com.nongpi.fulfillment.lot.api.dto;

import java.math.BigDecimal;

/**
 * 批次入库响应 DTO
 *
 * <p>由 LotController 入库接口返回，包含新生成批次的完整快照信息。</p>
 */
public record LotInboundResponse(
        String lotNo,
        Long skuId,
        String tempZone,
        String produceDate,
        String expireDate,
        BigDecimal initialQty,
        BigDecimal remainingQty,
        String status,
        Long supplierId
) {}
