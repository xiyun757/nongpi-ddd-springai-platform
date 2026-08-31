package com.nongpi.fulfillment.lot.domain;

import com.nongpi.fulfillment.common.domain.TempZone;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 批次出库领域事件
 *
 * <p>当批次通过 {@link Lot#outbound} 执行出库操作时触发。</p>
 *
 * @param lotNo      批次号
 * @param qty        出库数量
 * @param toLocation 目标库位/目的地
 * @param tempZone   温区
 * @param fullyOut   是否全部出清
 * @param occurredAt 事件发生时间
 */
public record LotOutboundedEvent(
        LotNo lotNo,
        BigDecimal qty,
        String toLocation,
        TempZone tempZone,
        boolean fullyOut,
        Instant occurredAt
) implements DomainEvent {

    public LotOutboundedEvent(LotNo lotNo, BigDecimal qty, String toLocation,
                              TempZone tempZone, boolean fullyOut) {
        this(lotNo, qty, toLocation, tempZone, fullyOut, Instant.now());
    }
}