package com.nongpi.fulfillment.common.exception;

/**
 * 资源未找到异常 — HTTP 404
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }

    public NotFoundException(String errorCode, String message) {
        super(404, errorCode, message);
    }
}
