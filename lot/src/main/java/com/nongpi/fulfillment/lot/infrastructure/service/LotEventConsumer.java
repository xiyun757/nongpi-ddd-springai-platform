package com.nongpi.fulfillment.lot.infrastructure.service;

import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.infrastructure.FefoCache;
import com.nongpi.fulfillment.lot.infrastructure.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Lot 领域事件消费者
 *
 * <p>监听 {@link RabbitMQConfig#QUEUE_NAME}，消费由 OutboxMessageRelay 投递的领域事件。
 * 通过 {@code t_mq_consume_log} 表的 message_id 唯一键实现幂等去重：
 * 先 INSERT 幂等记录（冲突=已消费，跳过），再执行业务逻辑，
 * 确保崩溃后重投不会重复执行。</p>
 *
 * <p>当前处理逻辑：</p>
 * <ul>
 *   <li>lot.inbound  → 将批次加入 FEFO 缓存（Redis ZSet）</li>
 *   <li>lot.outbound → 全部出清时从 FEFO 缓存中移除批次</li>
 *   <li>lot.transfer → 先从源温区移除，再加到目标温区（预留）</li>
 * </ul>
 */
@Component
public class LotEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LotEventConsumer.class);

    private static final String INBOUND_EVENT_SIMPLE_NAME = "LotInboundEvent";
    private static final String OUTBOUND_EVENT_SIMPLE_NAME = "LotOutboundedEvent";

    private final JdbcTemplate jdbcTemplate;
    private final FefoCache fefoCache;

    public LotEventConsumer(JdbcTemplate jdbcTemplate, FefoCache fefoCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.fefoCache = fefoCache;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    @Transactional
    public void onEvent(Map<String, Object> message) {
        Object eventIdObj = message.get("eventId");
        if (eventIdObj == null) {
            log.warn("收到无效事件（无 eventId），跳过: {}", message);
            return;
        }
        String eventId = eventIdObj.toString();

        // 先写幂等记录（唯一键冲突=已消费，直接跳过），再执行业务
        // 确保崩溃后重投不会重复执行业务逻辑
        try {
            jdbcTemplate.update(
                    "INSERT INTO t_mq_consume_log (message_id, consumer, consumed_at) VALUES (?, ?, ?)",
                    eventId, "LotEventConsumer", LocalDateTime.now()
            );
        } catch (DuplicateKeyException e) {
            log.debug("事件 {} 已消费，跳过", eventId);
            return;
        }

        String eventType = String.valueOf(message.get("eventType"));
        String simpleEventType = extractSimpleName(eventType);
        Object payload = message.get("payload");

        try {
            handleLotEvent(simpleEventType, payload);
            log.info("消费事件: type={}, id={}", simpleEventType, eventId);
        } catch (Exception e) {
            log.error("消费事件失败: type={}, id={}, error={}",
                    simpleEventType, eventId, e.getMessage(), e);
            // 抛出 AmqpRejectAndDontRequeueException，消息不再 requeue，
            // 而是通过 DLX 路由到死信队列 lot.event.dlq 供人工排查
            throw new AmqpRejectAndDontRequeueException("消费失败: " + e.getMessage(), e);
        }
    }

    private void handleLotEvent(String eventType, Object payload) {
        if (!(payload instanceof Map)) {
            log.warn("payload 格式异常，跳过缓存更新: {}", payload);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String, Object>) payload;

        String lotNo = getStringValue(payloadMap, "lotNo");
        if (lotNo == null || lotNo.isBlank()) {
            log.warn("事件缺少 lotNo，跳过缓存更新: {}", payloadMap);
            return;
        }

        switch (eventType) {
            case INBOUND_EVENT_SIMPLE_NAME -> handleInbound(lotNo, payloadMap);
            case OUTBOUND_EVENT_SIMPLE_NAME -> handleOutbound(lotNo, payloadMap);
            default -> log.debug("未识别事件类型，跳过: {}", eventType);
        }
    }

    private void handleInbound(String lotNo, Map<String, Object> payload) {
        TempZone tempZone = parseTempZone(getStringValue(payload, "tempZone"));
        LocalDate expireDate = parseLocalDate(getStringValue(payload, "expireDate"));

        if (tempZone == null || expireDate == null) {
            log.warn("入库事件缺少 tempZone 或 expireDate，跳过缓存更新: lotNo={}", lotNo);
            return;
        }

        fefoCache.addLot(lotNo, tempZone, expireDate);
        log.info("入库事件已更新 FEFO 缓存: lotNo={}, tempZone={}, expireDate={}",
                lotNo, tempZone, expireDate);
    }

    private void handleOutbound(String lotNo, Map<String, Object> payload) {
        TempZone tempZone = parseTempZone(getStringValue(payload, "tempZone"));
        Boolean fullyOut = getBooleanValue(payload, "fullyOut");

        if (tempZone == null) {
            log.warn("出库事件缺少 tempZone，跳过缓存更新: lotNo={}", lotNo);
            return;
        }

        if (fullyOut != null && fullyOut) {
            fefoCache.removeLot(lotNo, tempZone);
            log.info("出库事件已移除 FEFO 缓存: lotNo={}, tempZone={}", lotNo, tempZone);
        } else {
            log.debug("部分出库，不移除 FEFO 缓存: lotNo={}", lotNo);
        }
    }

    private static String extractSimpleName(String eventType) {
        if (eventType == null) {
            return "";
        }
        int dotIndex = eventType.lastIndexOf('.');
        return dotIndex >= 0 ? eventType.substring(dotIndex + 1) : eventType;
    }

    private static String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private static Boolean getBooleanValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return null;
    }

    private static TempZone parseTempZone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TempZone.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("无效的温区值: {}", value);
            return null;
        }
    }

    private static LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            log.warn("无效的日期值: {}", value);
            return null;
        }
    }
}
