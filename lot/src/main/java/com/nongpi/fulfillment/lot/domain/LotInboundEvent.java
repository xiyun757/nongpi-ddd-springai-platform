package com.nongpi.fulfillment.lot.domain;

import com.nongpi.fulfillment.common.domain.TempZone;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 批次入库领域事件
 *
 * <p>当新批次通过 {@link Lot#createNew} 创建时触发。</p>
 *
 * @param lotNo      批次号
 * @param qty        入库数量
 * @param supplierId 供应商 ID
 * @param tempZone   温区
 * @param expireDate 过期日期
 * @param occurredAt 事件发生时间
 */
public record LotInboundEvent(
        LotNo lotNo,
        BigDecimal qty,
        Long supplierId,
        TempZone tempZone,
        LocalDate expireDate,
        Instant occurredAt
) implements DomainEvent {

    public LotInboundEvent(LotNo lotNo, BigDecimal qty, Long supplierId,
                           TempZone tempZone, LocalDate expireDate) {
        this(lotNo, qty, supplierId, tempZone, expireDate, Instant.now());
    }
}