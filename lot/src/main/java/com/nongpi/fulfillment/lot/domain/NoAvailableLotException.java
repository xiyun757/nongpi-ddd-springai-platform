package com.nongpi.fulfillment.lot.domain;

import com.nongpi.fulfillment.common.exception.BusinessException;

/**
 * 无可用批次异常 — 出库时没有满足条件的可用批次
 */
public class NoAvailableLotException extends BusinessException {

    public NoAvailableLotException() {
        super(404, "NO_AVAILABLE_LOT", "没有可用的批次");
    }

    public NoAvailableLotException(String message) {
        super(404, "NO_AVAILABLE_LOT", message);
    }
}