package com.nongpi.fulfillment.lot.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 出库转移记录 — {@link Lot#outbound} 的返回值
 *
 * <p>表示一次出库操作产生的物品转移凭证。</p>
 *
 * @param lotNo        批次号
 * @param qty          出库数量
 * @param toLocation   目标库位/目的地
 * @param transferredAt 转移时间
 * @param remainingQty 出库后批次剩余数量
 */
public record LotTransfer(
        LotNo lotNo,
        BigDecimal qty,
        String toLocation,
        Instant transferredAt,
        BigDecimal remainingQty
) {

    public LotTransfer(LotNo lotNo, BigDecimal qty, String toLocation, BigDecimal remainingQty) {
        this(lotNo, qty, toLocation, Instant.now(), remainingQty);
    }
}