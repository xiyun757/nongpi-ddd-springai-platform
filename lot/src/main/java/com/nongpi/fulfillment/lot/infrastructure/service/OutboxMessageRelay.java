package com.nongpi.fulfillment.lot.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.fulfillment.lot.infrastructure.config.RabbitMQConfig;
import com.nongpi.fulfillment.lot.infrastructure.mapper.LotEventMapper;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotEventPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Outbox 消息中继 — 定时扫描未发布领域事件并发送到 RabbitMQ
 *
 * <p>每 2 秒扫描 t_lot_event 表中 {@code status='PENDING'} 的记录，
 * 发送到 RabbitMQ Exchange {@link RabbitMQConfig#EXCHANGE_NAME}。</p>
 *
 * <p><b>可靠性保证（基于 Publisher Confirm 的 ack/nack 驱动状态机）</b>：</p>
 * <ul>
 *   <li>发送前先将 status 标记为 IN_FLIGHT，防止定时任务重复投递</li>
 *   <li>Publisher Confirm 回调：
 *     <ul>
 *       <li>ack  → 标记 published=1, status=PUBLISHED（投递成功）</li>
 *       <li>nack → retry_count++，若 &lt; MAX_RETRY 则 status=PENDING 等待重试；
 *           若 &ge; MAX_RETRY 则 status=DEAD，记录 error 日志告警人工处理</li>
 *     </ul>
 *   </li>
 *   <li>CorrelationData — 携带 eventId，便于关联确认回调</li>
 *   <li>mandatory=true — 找不到 Queue 时触发 returns 回调（配置级）</li>
 * </ul>
 *
 * <p><b>注意</b>：若应用在 IN_FLIGHT 状态时崩溃，消息会卡在该状态。
 * 定时任务会自动恢复超时（>30s）的 IN_FLIGHT 记录为 PENDING 重新投递。</p>
 */
@Service
public class OutboxMessageRelay implements RabbitTemplate.ConfirmCallback {

    private static final Logger log = LoggerFactory.getLogger(OutboxMessageRelay.class);

    private static final int BATCH_SIZE = 50;

    /** 最大重试次数，超过后标记为 DEAD */
    private static final int MAX_RETRY = 3;

    /** IN_FLIGHT 超时阈值（秒），超时后重置为 PENDING 重试 */
    private static final int IN_FLIGHT_TIMEOUT_SECONDS = 30;

    private final LotEventMapper lotEventMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${outbox.relay.enabled:true}")
    private boolean relayEnabled;

    public OutboxMessageRelay(LotEventMapper lotEventMapper,
                              RabbitTemplate rabbitTemplate,
                              ObjectMapper objectMapper) {
        this.lotEventMapper = lotEventMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        // 注册 Publisher Confirm 回调
        this.rabbitTemplate.setConfirmCallback(this);
    }

    @Scheduled(fixedDelay = 2000)
    public void relayUnpublishedEvents() {
        if (!relayEnabled) {
            return;
        }

        // 0. 恢复超时的 IN_FLIGHT 事件（应用崩溃后 ConfirmCallback 不会再触发）
        recoverTimedOutInFlightEvents();

        // 1. 拉取待发布事件（status=PENDING，每次最多 BATCH_SIZE 条）
        LambdaQueryWrapper<LotEventPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LotEventPO::getStatus, LotEventPO.STATUS_PENDING);
        wrapper.last("LIMIT " + BATCH_SIZE);
        List<LotEventPO> events = lotEventMapper.selectList(wrapper);

        if (events.isEmpty()) {
            return;
        }

        log.info("OutboxRelay: 发现 {} 条待发布事件", events.size());

        for (LotEventPO event : events) {
            try {
                // 2. 先标记为 IN_FLIGHT，防止定时任务重复投递
                event.setStatus(LotEventPO.STATUS_IN_FLIGHT);
                event.setInFlightAt(LocalDateTime.now()); // 记录进入 IN_FLIGHT 的时间，用于超时恢复
                lotEventMapper.updateById(event);

                // 3. 发送到 RabbitMQ（带 CorrelationData 便于确认追踪）
                Map<String, Object> message = Map.of(
                        "eventId", event.getId(),
                        "aggregateId", event.getAggregateId(),
                        "eventType", event.getEventType(),
                        "payload", parsePayload(event.getPayload()),
                        "timestamp", event.getCreatedAt()
                );

                String routingKey = RabbitMQConfig.ROUTING_KEY_PATTERN_PREFIX
                        + extractSimpleName(event.getEventType());
                CorrelationData correlationData = new CorrelationData(event.getId().toString());
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME, routingKey, message, correlationData);

                log.debug("OutboxRelay: 已发送事件 id={} type={}, 等待 Confirm 回调",
                        event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("OutboxRelay: 发送事件失败 id={} type={}, error={}",
                        event.getId(), event.getEventType(), e.getMessage(), e);
                // 发送失败，回滚状态为 PENDING 以便下次重试
                event.setStatus(LotEventPO.STATUS_PENDING);
                event.setInFlightAt(null);
                lotEventMapper.updateById(event);
            }
        }
    }

    /**
     * Publisher Confirm 回调 — 根据 ack/nack 更新事件状态
     *
     * <p>ack：消息已到达 Exchange，标记 PUBLISHED<br>
     * nack：Broker 未确认，增加 retry_count，超限则标记 DEAD</p>
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (correlationData == null) {
            return;
        }
        String eventIdStr = correlationData.getId();
        Long eventId;
        try {
            eventId = Long.parseLong(eventIdStr);
        } catch (NumberFormatException e) {
            log.warn("Confirm 回调: 无法解析 eventId={}", eventIdStr);
            return;
        }

        LotEventPO event = lotEventMapper.selectById(eventId);
        if (event == null) {
            log.warn("Confirm 回调: 事件不存在 eventId={}", eventId);
            return;
        }

        if (ack) {
            // 收到 ack，标记已发布
            event.setPublished(1);
            event.setStatus(LotEventPO.STATUS_PUBLISHED);
            event.setInFlightAt(null);
            lotEventMapper.updateById(event);
            log.info("Confirm ack: 事件 {} 投递成功", eventId);
        } else {
            // 收到 nack，增加重试计数
            int newRetryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
            event.setRetryCount(newRetryCount);
            event.setInFlightAt(null);

            if (newRetryCount >= MAX_RETRY) {
                // 超过最大重试次数，标记为 DEAD，告警人工处理
                event.setStatus(LotEventPO.STATUS_DEAD);
                lotEventMapper.updateById(event);
                log.error("Confirm nack: 事件 {} 投递失败 {}/{} 次，已标记 DEAD, cause={}",
                        eventId, newRetryCount, MAX_RETRY, cause);
            } else {
                // 重置为 PENDING，等待下次扫描重试
                event.setStatus(LotEventPO.STATUS_PENDING);
                lotEventMapper.updateById(event);
                log.warn("Confirm nack: 事件 {} 投递失败 {}/{} 次，将重试, cause={}",
                        eventId, newRetryCount, MAX_RETRY, cause);
            }
        }
    }

    /**
     * 恢复超时的 IN_FLIGHT 事件 — 应用崩溃后 ConfirmCallback 不会再触发，
     * 这些事件会永远卡在 IN_FLIGHT。定时重置超时（>30s）的 IN_FLIGHT 为 PENDING。
     * 用 in_flight_at（进入 IN_FLIGHT 的时间）判断，不用 created_at（事件创建时间）。
     */
    private void recoverTimedOutInFlightEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(IN_FLIGHT_TIMEOUT_SECONDS);
        LambdaUpdateWrapper<LotEventPO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LotEventPO::getStatus, LotEventPO.STATUS_IN_FLIGHT)
                     .lt(LotEventPO::getInFlightAt, cutoff)
                     .set(LotEventPO::getStatus, LotEventPO.STATUS_PENDING)
                     .set(LotEventPO::getInFlightAt, null);
        int recovered = lotEventMapper.update(null, updateWrapper);
        if (recovered > 0) {
            log.warn("恢复 {} 条超时 IN_FLIGHT 事件为 PENDING（超过 {}s 未确认）",
                    recovered, IN_FLIGHT_TIMEOUT_SECONDS);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("OutboxRelay: 解析事件 payload 失败, 返回原始字符串", e);
            return Map.of("raw", payload);
        }
    }

    /**
     * 从全限定类名提取简单类名：com.nongpi...LotInboundEvent -> LotInboundEvent
     */
    private static String extractSimpleName(String eventType) {
        if (eventType == null) {
            return "";
        }
        int dotIndex = eventType.lastIndexOf('.');
        return dotIndex >= 0 ? eventType.substring(dotIndex + 1) : eventType;
    }
}
