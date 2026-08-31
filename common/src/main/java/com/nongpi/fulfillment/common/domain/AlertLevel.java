package com.nongpi.fulfillment.common.domain;

/**
 * 预警级别枚举 — 农批履约中台
 *
 * <p>定义预警的严重程度，级别递增：INFO → WARNING → CRITICAL。</p>
 */
public enum AlertLevel {

    /** 提示 — 仅通知，无需紧急处理 */
    INFO,

    /** 警告 — 需要关注，建议尽快处理 */
    WARNING,

    /** 严重 — 必须立即处理，影响业务安全 */
    CRITICAL
}
