package com.nongpi.fulfillment.lot.infrastructure;

/**
 * FEFO 策略不可用异常 — Redis 策略无法完成时抛出，
 * 供 {@link FefoStrategyFactory} 捕获后降级为数据库策略。
 */
public class FefoStrategyUnavailableException extends RuntimeException {
    public FefoStrategyUnavailableException(String message) {
        super(message);
    }
}
