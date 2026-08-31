package com.nongpi.fulfillment.lot.domain;

import com.nongpi.fulfillment.common.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 库存不足异常 — 出库数量超过批次剩余数量时抛出
 */
public class InsufficientQtyException extends BusinessException {

    private final BigDecimal requestedQty;
    private final BigDecimal availableQty;

    public InsufficientQtyException(BigDecimal requestedQty, BigDecimal availableQty) {
        super(409, "INSUFFICIENT_QTY", "Insufficient quantity: requested "
                + requestedQty.toPlainString() + " but only "
                + availableQty.toPlainString() + " available");
        this.requestedQty = requestedQty;
        this.availableQty = availableQty;
    }

    public BigDecimal requestedQty() {
        return requestedQty;
    }

    public BigDecimal availableQty() {
        return availableQty;
    }
}