package com.nongpi.fulfillment.common.exception;

import lombok.Getter;

/**
 * 业务异常基类 — 统一异常体系根
 * <p>每个业务异常携带 HTTP 状态码和业务错误码。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String errorCode;

    public BusinessException(int code, String errorCode, String message) {
        super(message);
        this.code = code;
        this.errorCode = errorCode;
    }
}
