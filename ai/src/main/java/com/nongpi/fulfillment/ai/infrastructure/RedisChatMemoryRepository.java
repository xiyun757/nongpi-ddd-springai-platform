package com.nongpi.fulfillment.ai.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis 持久化 ChatMemory 仓库 — 替代 InMemoryChatMemoryRepository 防内存泄漏
 *
 * <p>每个会话存一个 Redis key {@code chatmemory:<chatId>}，value 为 JSON 序列化的
 * {@code List<{role, content}>} DTO。每次 saveAll 刷新 TTL（1 小时），过期自动清理，
 * 避免会话无限累积导致 OOM。</p>
 *
 * <p><b>含金量</b>：对话记忆持久化到 Redis，应用重启不丢失；多实例部署共享会话；
 * TTL 自动清理过期会话。对比 InMemory（重启丢失 + 内存泄漏），更贴近生产可用。</p>
 *
 * <p>Spring AI 1.1.2 BOM 未提供 Redis ChatMemoryRepository（只有 jdbc/
 * cassandra/mongodb 等），自行实现 {@link ChatMemoryRepository} 4 方法，复用项目
 * 已有的 {@link RedissonClient} 连接池（与 FefoCache 共用），无需引入新依赖。</p>
 *
 * <p>序列化方案：Message 子类（UserMessage/AssistantMessage 等）不可变，无默认构造函数，
 * Jackson 直接反序列化会失败。改为转成简单的 {@code {role, content}} DTO 存储，
 * 反序列化时根据 role 重建 Message 对象。ChatMemory 只需 role + content 提供上下文，
 * 不需要 metadata（tool calls / finish reason 等）。</p>
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryRepository.class);
    private static final String KEY_PREFIX = "chatmemory:";
    private static final Duration TTL = Duration.ofHours(1);

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public RedisChatMemoryRepository(RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        // copy 避免污染全局 ObjectMapper；DTO 是简单 POJO，无需类型信息
        this.objectMapper = objectMapper.copy();
    }

    @Override
    public List<String> findConversationIds() {
        List<String> ids = new ArrayList<>();
        Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(KEY_PREFIX + "*");
        for (String key : keys) {
            ids.add(key.substring(KEY_PREFIX.length()));
        }
        return ids;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + conversationId, StringCodec.INSTANCE);
            String json = bucket.get();
            if (json == null) return new ArrayList<>();
            List<Map<String, String>> dtoList = objectMapper.readValue(json, new TypeReference<>() {});
            List<Message> messages = new ArrayList<>();
            for (Map<String, String> dto : dtoList) {
                String role = dto.get("role");
                String content = dto.get("content");
                messages.add(switch (role) {
                    case "USER" -> new UserMessage(content);
                    case "ASSISTANT" -> new AssistantMessage(content);
                    case "SYSTEM" -> new SystemMessage(content);
                    default -> new UserMessage(content);
                });
            }
            return messages;
        } catch (Exception e) {
            log.warn("ChatMemory findById 失败 conversationId={}: {}", conversationId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            // 转 DTO 避免反序列化不可变 Message 子类
            List<Map<String, String>> dtoList = new ArrayList<>();
            for (Message msg : messages) {
                dtoList.add(Map.of(
                        "role", msg.getMessageType().name(),
                        "content", msg.getText()
                ));
            }
            String json = objectMapper.writeValueAsString(dtoList);
            RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + conversationId, StringCodec.INSTANCE);
            bucket.set(json, TTL);
        } catch (Exception e) {
            log.warn("ChatMemory saveAll 失败 conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redissonClient.getBucket(KEY_PREFIX + conversationId, StringCodec.INSTANCE).delete();
    }
}
