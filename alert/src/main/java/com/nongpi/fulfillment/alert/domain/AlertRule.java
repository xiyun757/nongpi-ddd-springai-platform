package com.nongpi.fulfillment.alert.domain;

import com.nongpi.fulfillment.common.domain.AlertLevel;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.common.exception.ValidationException;
import lombok.Getter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * AlertRule 聚合根 — 预警规则
 *
 * <p>定义批次过期预警的触发条件：当批次过期日期距今不超过 thresholdDays 天时触发预警。
 * skuId 为 null 表示全局规则；tempZone 为 null 表示适用于所有温区。</p>
 */
@Getter
public class AlertRule {

    private Long id;
    private final Long skuId;
    private final TempZone tempZone;
    private final int thresholdDays;
    private final AlertLevel alertLevel;
    private boolean enabled;

    // ── 构造器 ──────────────────────────────────────────────

    private AlertRule(Long skuId, TempZone tempZone, int thresholdDays, AlertLevel alertLevel) {
        this.skuId = skuId;
        this.tempZone = tempZone;
        this.thresholdDays = thresholdDays;
        this.alertLevel = alertLevel;
        this.enabled = true;
    }

    // ── 静态工厂方法 ────────────────────────────────────────

    /**
     * 创建预警规则
     *
     * @param skuId         SKU 标识（null 表示全局规则）
     * @param tempZone      温区（null 表示所有温区）
     * @param thresholdDays 过期警戒天数
     * @param alertLevel    预警级别
     * @return AlertRule 聚合根
     */
    public static AlertRule create(Long skuId, TempZone tempZone,
                                   int thresholdDays, AlertLevel alertLevel) {
        Objects.requireNonNull(alertLevel, "预警级别不能为空");
        if (thresholdDays <= 0) {
            throw new ValidationException("过期警戒天数必须大于 0");
        }
        return new AlertRule(skuId, tempZone, thresholdDays, alertLevel);
    }

    /**
     * 从持久化数据还原
     */
    public static AlertRule reconstitute(Long id, Long skuId, TempZone tempZone,
                                         int thresholdDays, AlertLevel alertLevel,
                                         boolean enabled) {
        AlertRule rule = new AlertRule(skuId, tempZone, thresholdDays, alertLevel);
        rule.id = id;
        rule.enabled = enabled;
        return rule;
    }

    // ── 业务方法 ────────────────────────────────────────────

    /**
     * 判断指定过期日期是否应触发预警
     *
     * @param expireDate 批次过期日期
     * @return true 如果距今不超过 thresholdDays 天
     */
    public boolean shouldAlert(LocalDate expireDate) {
        if (!enabled) {
            return false;
        }
        long daysUntilExpire = ChronoUnit.DAYS.between(LocalDate.now(), expireDate);
        return daysUntilExpire >= 0 && daysUntilExpire <= thresholdDays;
    }

    /**
     * 判断此规则是否适用于指定 SKU + 温区
     *
     * @param skuId    SKU 标识
     * @param tempZone 温区
     * @return true 如果规则适用
     */
    public boolean matches(Long skuId, TempZone tempZone) {
        if (!enabled) {
            return false;
        }
        boolean skuMatch = (this.skuId == null) || this.skuId.equals(skuId);
        boolean zoneMatch = (this.tempZone == null) || this.tempZone == tempZone;
        return skuMatch && zoneMatch;
    }

    /**
     * 启用规则
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * 禁用规则
     */
    public void disable() {
        this.enabled = false;
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
