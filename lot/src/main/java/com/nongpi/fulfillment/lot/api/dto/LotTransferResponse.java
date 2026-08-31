package com.nongpi.fulfillment.lot.api.dto;

import java.math.BigDecimal;

/**
 * 批次出入库记录响应 DTO — 独立于 Controller 的 API 层对象
 *
 * <p>由 {@code lot.application.LotQueryService} 组装并返回，
 * 应用层不依赖 Controller 嵌套类型（DDD 分层倒置修复）。</p>
 *
 * @param id            记录 ID（t_lot_transfer 主键）
 * @param lotNo         批次号
 * @param type          类型：INBOUND / OUTBOUND / TRANSFER
 * @param qty           数量
 * @param fromLocation  来源库位
 * @param toLocation    目标库位
 * @param operator      操作人
 * @param createdAt     记录创建时间（ISO 字符串）
 */
public record LotTransferResponse(
        Long id,
        String lotNo,
        String type,
        BigDecimal qty,
        String fromLocation,
        String toLocation,
        String operator,
        String createdAt
) {}
