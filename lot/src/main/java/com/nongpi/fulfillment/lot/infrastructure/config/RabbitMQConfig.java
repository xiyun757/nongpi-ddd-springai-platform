package com.nongpi.fulfillment.lot.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 — Exchange / Queue / Binding 声明
 *
 * <p>声明 lot.event Topic Exchange 与 lot.event.queue 持久化队列，
 * 路由键匹配 {@code lot.*}（lot.inbound / lot.outbound / lot.transfer 等）。</p>
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
    public static final String ROUTING_KEY_PATTERN = "lot.*";

    // 死信队列相关常量
    public static final String DLX_NAME = "lot.event.dlx";
    public static final String DLQ_NAME = "lot.event.dlq";
    public static final String DLQ_ROUTING_KEY = "dead";

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
