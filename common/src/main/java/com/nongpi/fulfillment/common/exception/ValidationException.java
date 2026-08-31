package com.nongpi.fulfillment.common.exception;

/**
 * 参数校验异常 — HTTP 422
 */
public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(422, "VALIDATION_ERROR", message);
    }

    public ValidationException(String errorCode, String message) {
        super(422, errorCode, message);
    }
}
