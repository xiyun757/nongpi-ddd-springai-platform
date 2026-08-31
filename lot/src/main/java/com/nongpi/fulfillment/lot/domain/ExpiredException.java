package com.nongpi.fulfillment.lot.domain;

import com.nongpi.fulfillment.common.exception.BusinessException;

import java.time.LocalDate;

/**
 * 批次过期异常 — 尝试对已过期批次执行出库操作时抛出
 */
public class ExpiredException extends BusinessException {

    private final LocalDate expireDate;

    public ExpiredException(LocalDate expireDate) {
        super(409, "LOT_EXPIRED", "Lot has expired on " + expireDate + " and cannot be outbounded");
        this.expireDate = expireDate;
    }

    public LocalDate expireDate() {
        return expireDate;
    }
}