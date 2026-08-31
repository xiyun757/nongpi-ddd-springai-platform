package com.nongpi.fulfillment.lot.domain;

import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.common.exception.BusinessException;

/**
 * 温区不匹配异常 — 出库目标库位温区与批次温区不一致时抛出
 */
public class TempZoneMismatchException extends BusinessException {

    private final TempZone lotZone;
    private final TempZone targetZone;

    public TempZoneMismatchException(TempZone lotZone, TempZone targetZone) {
        super(409, "TEMP_ZONE_MISMATCH",
                "Temp zone mismatch: lot requires " + lotZone + " but target is " + targetZone);
        this.lotZone = lotZone;
        this.targetZone = targetZone;
    }

    public TempZone lotZone() {
        return lotZone;
    }

    public TempZone targetZone() {
        return targetZone;
    }
}