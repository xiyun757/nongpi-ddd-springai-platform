package com.nongpi.fulfillment.lot.domain;

import java.time.Instant;

/**
 * 领域事件接口 — 所有领域事件的基类
 *
 * <p>每个事件携带发生时间 {@link #occurredAt()}，由聚合根收集，
 * 经仓储在 {@code save()} 时持久化到 Outbox 表。</p>
 */
public interface DomainEvent {

    /**
     * 事件发生时间。
     *
     * @return 不可变的时间戳
     */
    Instant occurredAt();
}