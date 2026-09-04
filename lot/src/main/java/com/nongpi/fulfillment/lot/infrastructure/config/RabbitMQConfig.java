package com.nongpi.fulfillment.lot.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 — Exchange / Queue / Binding / 消息转换器声明
 *
 * <p>声明 lot.event Topic Exchange 与 lot.event.queue 持久化队列，
 * 路由键匹配 {@code lot.*}（lot.inbound / lot.outbound / lot.transfer 等）。</p>
 *
 * <p>消息序列化统一用 {@code Jackson2JsonMessageConverter}（JSON），
 * 避免默认 {@code SimpleMessageConverter} 走 JDK 原生序列化产生
 * {@code java.util.CollSer} 代理类，在 Spring AMQP 3.2+ 开启的反序列化
 * 白名单下抛 {@code SecurityException}。</p>
 *
 * <p>OutboxMessageRelay 投递的消息会路由到此 Queue，由
 * {@link com.nongpi.fulfillment.lot.infrastructure.service.LotEventConsumer} 消费。</p>
 *
 * <p><b>死信队列配置</b>：</p>
 * <ul>
 *   <li>主队列 {@code lot.event.queue} 配置 {@code x-dead-letter-exchange=lot.event.dlx}
 *       和 {@code x-dead-letter-routing-key=dead}</li>
 *   <li>死信交换机 {@code lot.event.dlx}（Direct 类型，持久化）</li>
 *   <li>死信队列 {@code lot.event.dlq}（持久化），绑定到 DLX 路由键 {@code dead}</li>
 *   <li>消费者抛出 {@code AmqpRejectAndDontRequeueException} 后，消息不再 requeue，
 *       而是通过 DLX 路由到 DLQ 供人工排查</li>
 * </ul>
 *
 * <p><b>注意</b>：修改主队列参数（添加 dead-letter 参数）后，需先在 RabbitMQ 管理界面
 * 删除旧的 {@code lot.event.queue} 再重启应用，否则队列声明会因参数不匹配而失败。</p>
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "lot.event";
    public static final String QUEUE_NAME = "lot.event.queue";
    /** Topic Exchange 绑定模式（单层匹配）。实际 routing key 由本常量 + 事件简单类名拼成 */
    public static final String ROUTING_KEY_PATTERN = "lot.*";
    /** routing key 前缀，与 ROUTING_KEY_PATTERN 的 "lot." 对齐 */
    public static final String ROUTING_KEY_PATTERN_PREFIX = "lot.";

    // 死信队列相关常量
    public static final String DLX_NAME = "lot.event.dlx";
    public static final String DLQ_NAME = "lot.event.dlq";
    public static final String DLQ_ROUTING_KEY = "dead";

    /**
     * 消息转换器 — 统一使用 JSON 序列化。
     *
     * <p>Spring AMQP 3.2+ 默认开启反序列化白名单（serialization-whitelist），
     * 拒绝默认 {@code SimpleMessageConverter} 的 JDK 原生序列化（会生成
     * {@code java.util.CollSer} 代理类）。切到 {@code Jackson2JsonMessageConverter}
     * 后，消息以 JSON 形式传输，两端一致，白名单不再触发。</p>
     *
     * <p>注册 {@code JavaTimeModule}，保证消息中 {@code LocalDateTime}（如
     * OutboxMessageRelay 的 {@code timestamp} 字段）能正常序列化/反序列化，
     * 而非抛 {@code InvalidDefinitionException: java.time.LocalDateTime}。</p>
     *
     * <p>仅声明 {@code MessageConverter} Bean，不覆盖 {@code RabbitTemplate}：
     * Spring Boot 自动配置会把唯一的 MessageConverter 注入自动构建的
     * RabbitTemplate 与 RabbitListenerContainerFactory，同时保留
     * application.yml 的 {@code mandatory/publisher-confirm-type/publisher-returns}
     * 等模板级配置（Outbox 的 Publisher Confirm 可靠性依赖它们）。</p>
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public TopicExchange lotEventExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_NAME)
                .durable(true)
                .build();
    }

    /**
     * 主队列 — 配置死信交换机参数，消费失败的消息路由到 DLQ
     */
    @Bean
    public Queue lotEventQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding lotEventBinding(Queue lotEventQueue, TopicExchange lotEventExchange) {
        return BindingBuilder.bind(lotEventQueue)
                .to(lotEventExchange)
                .with(ROUTING_KEY_PATTERN);
    }

    // ── 死信队列配置 ──

    @Bean
    public DirectExchange lotEventDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX_NAME)
                .durable(true)
                .build();
    }

    @Bean
    public Queue lotEventDeadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME)
                .build();
    }

    @Bean
    public Binding lotEventDeadLetterBinding(Queue lotEventDeadLetterQueue,
                                              DirectExchange lotEventDeadLetterExchange) {
        return BindingBuilder.bind(lotEventDeadLetterQueue)
                .to(lotEventDeadLetterExchange)
                .with(DLQ_ROUTING_KEY);
    }
}
