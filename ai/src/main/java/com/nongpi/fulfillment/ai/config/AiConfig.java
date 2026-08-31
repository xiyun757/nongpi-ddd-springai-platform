package com.nongpi.fulfillment.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.fulfillment.ai.infrastructure.RedisChatMemoryRepository;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 模块配置 — ChatClient + ChatMemory Bean 初始化
 * <p>
 * LLM 通过 OpenAI 兼容协议接入（Ollama / 通义千问 / 日日新），
 * 切换提供商仅需修改 application.yml 中的 base-url 和 api-key。
 * </p>
 */
@Configuration
public class AiConfig {

    private static final String SYSTEM_PROMPT = """
            你是农批履约中台的智能客服助手。
            你可以帮助用户：
            - 查询批次状态、库存信息、预警记录
            - 执行入库、出库、转库、库存调整等操作
            - 回答冷链存储规范、批次管理流程等问题
            - 对复杂任务进行多步规划和执行
            请根据用户需求选择合适的能力，用简洁专业的中文回答。
            """;

    /**
     * 对话记忆仓库 — Redis 持久化（替代 InMemoryChatMemoryRepository）
     * <p>Spring AI 1.1.2 BOM 未提供 Redis ChatMemoryRepository，自行实现 {@link RedisChatMemoryRepository}，
     * 复用项目已有的 RedissonClient 连接池。每个会话存 Redis key {@code chatmemory:<chatId>}，
     * TTL 1 小时自动清理，防内存泄漏；应用重启对话不丢失；多实例共享会话。</p>
     */
    @Bean
    @Primary
    public ChatMemoryRepository chatMemoryRepository(RedissonClient redissonClient, ObjectMapper objectMapper) {
        return new RedisChatMemoryRepository(redissonClient, objectMapper);
    }

    /**
     * 对话记忆 — 基于 Redis 仓库，单会话保留最近 20 条消息（窗口淘汰最早）
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * ChatClient — Spring AI 的核心客户端，链式 API
     * <p>Advisor 链：MessageChatMemoryAdvisor（多轮记忆）+ SimpleLoggerAdvisor（请求响应日志）</p>
     * <p>不挂 defaultTools — Spring AI 1.1.2 的 stream() 不自动执行工具调用循环，
     * defaultTools 会导致 LLM 触发工具时流式 Flux 挂死（同步 .call() 能自动处理工具循环故不受影响）。
     * 工具调用由 {@link com.nongpi.fulfillment.ai.app.NongpiAssistantApp#chat} 同步方法显式挂载；
     * MCP 工具同理不挂默认，避免 LLM 误触发 read_file 导致流式挂死。Manus 用独立 ReAct 框架不受影响。</p>
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                )
                .build();
    }
}

