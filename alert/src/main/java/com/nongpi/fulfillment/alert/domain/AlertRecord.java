package com.nongpi.fulfillment.alert.domain;

import com.nongpi.fulfillment.common.domain.AlertLevel;
import com.nongpi.fulfillment.common.exception.BusinessException;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AlertRecord 实体 — 预警记录
 *
 * <p>记录每次预警的详细信息，包括关联的批次、规则、级别和处理状态。</p>
 */
@Getter
public class AlertRecord {

    private Long id;
    private final String lotNo;
    private final Long alertRuleId;
    private final AlertLevel alertLevel;
    private final String message;
    private boolean handled;
    private String handler;
    private LocalDateTime handledAt;
    private final LocalDateTime createdAt;

    // ── 构造器 ──────────────────────────────────────────────

    private AlertRecord(String lotNo, Long alertRuleId, AlertLevel alertLevel, String message) {
        this.lotNo = lotNo;
        this.alertRuleId = alertRuleId;
        this.alertLevel = alertLevel;
        this.message = message;
        this.handled = false;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 内部构造器 — 供 reconstitute 使用
     */
    private AlertRecord(String lotNo, Long alertRuleId, AlertLevel alertLevel,
                        String message, LocalDateTime createdAt) {
        this.lotNo = lotNo;
        this.alertRuleId = alertRuleId;
        this.alertLevel = alertLevel;
        this.message = message;
        this.handled = false;
        this.createdAt = createdAt;
    }

    // ── 静态工厂方法 ────────────────────────────────────────

    /**
     * 创建预警记录
     *
     * @param lotNo       批次号
     * @param alertRuleId 关联预警规则 ID
     * @param alertLevel  预警级别
     * @param message     预警消息
     * @return AlertRecord 实体
     */
    public static AlertRecord create(String lotNo, Long alertRuleId,
                                     AlertLevel alertLevel, String message) {
        Objects.requireNonNull(lotNo, "批次号不能为空");
        Objects.requireNonNull(alertLevel, "预警级别不能为空");
        Objects.requireNonNull(message, "预警消息不能为空");
        return new AlertRecord(lotNo, alertRuleId, alertLevel, message);
    }

    /**
     * 从持久化数据还原
     */
    public static AlertRecord reconstitute(Long id, String lotNo, Long alertRuleId,
                                           AlertLevel alertLevel, String message,
                                           boolean handled, String handler,
                                           LocalDateTime handledAt, LocalDateTime createdAt) {
        AlertRecord record = new AlertRecord(lotNo, alertRuleId, alertLevel, message, createdAt);
        record.id = id;
        record.handled = handled;
        record.handler = handler;
        record.handledAt = handledAt;
        return record;
    }

    // ── 业务方法 ────────────────────────────────────────────

    /**
     * 标记预警已处理
     *
     * @param handlerName 处理人姓名
     */
    public void handle(String handlerName) {
        Objects.requireNonNull(handlerName, "处理人不能为空");
        if (this.handled) {
            throw new BusinessException(409, "ALREADY_HANDLED", "预警已处理，不可重复操作");
        }
        this.handled = true;
        this.handler = handlerName;
        this.handledAt = LocalDateTime.now();
    }

    /**
     * 供仓储层在 insert 后回填自增主键使用，业务代码不应调用。
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已存在，不允许重复赋值");
        }
        this.id = id;
    }
}
