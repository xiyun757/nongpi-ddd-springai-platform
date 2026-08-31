package com.nongpi.fulfillment.common.infrastructure;

import com.nongpi.fulfillment.common.domain.OptimisticLockException;
import com.nongpi.fulfillment.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通用全局异常处理器 — 兜底所有未被模块特定处理器匹配的异常
 *
 * <p>统一拦截异常并返回标准错误体。错误体格式：</p>
 * <pre>
 * { "timestamp": ..., "status": 404, "error": "...", "errorCode": "NOT_FOUND" }
 * </pre>
 *
 * <p><b>优先级</b>：{@link Ordered#LOWEST_PRECEDENCE}，作为兜底处理器。
 * 模块特定的 {@code @RestControllerAdvice} 应使用更高优先级（数值更小）以优先匹配模块特定异常。</p>
 *
 * <p><b>改名原因</b>：原类名 {@code GlobalExceptionHandler} 易与各业务模块的同名类冲突，
 * Spring 默认用类名首字母小写生成 beanName（{@code globalExceptionHandler}），
 * 导致 {@link org.springframework.context.annotation.ConflictingBeanDefinitionException}。
 * 统一改为 {@code CommonGlobalExceptionHandler} 避免命名冲突。</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class CommonGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CommonGlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getCode()).body(errorBody(
                e.getCode(), e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getField() + ": " + fe.getDefaultMessage() : "参数校验失败";
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorBody(422, msg, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(409, e.getMessage(), "OPTIMISTIC_LOCK"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(400, e.getMessage(), "BAD_REQUEST"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(409, e.getMessage(), "CONFLICT"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorBody(403, "无权限访问该资源", "ACCESS_DENIED"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody(401, "认证失败：" + e.getMessage(), "UNAUTHORIZED"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleInternal(Exception e) {
        log.error("Internal server error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(500, "服务器内部错误", "INTERNAL_ERROR"));
    }

    private Map<String, Object> errorBody(int status, String message, String errorCode) {
        return Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status,
                "error", message == null ? "" : message,
                "errorCode", errorCode
        );
    }
}
