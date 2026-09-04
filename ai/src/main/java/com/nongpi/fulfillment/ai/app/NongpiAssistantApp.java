package com.nongpi.fulfillment.ai.app;

import com.nongpi.fulfillment.ai.rag.QueryRewriter;
import com.nongpi.fulfillment.ai.rag.RagAdvisorFactory;
import com.nongpi.fulfillment.ai.rag.RagCategoryInferrer;
import com.nongpi.fulfillment.ai.tools.NongpiToolSet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 农批智能客服核心编排类
 * <p>
 * P0：基础对话 + 流式对话 + 多轮记忆 + ToolCalling
 * P2：RAG 对话（QueryRewriter 重写查询 → 推断 category → 动态 RetrievalAugmentationAdvisor 检索后回答）
 * P3：Manus（ReAct 智能体，见 agent 包）
 * </p>
 */
@Component
public class NongpiAssistantApp {

    private static final Logger log = LoggerFactory.getLogger(NongpiAssistantApp.class);

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;
    /** 模拟流式：每段字符数 */
    private static final int CHUNK_SIZE = 2;
    /** 模拟流式：每段间隔毫秒 */
    private static final long CHUNK_DELAY_MS = 30;

    private final ChatClient chatClient;
    private final NongpiToolSet nongpiToolSet;
    /** MCP 工具（weather/filesystem），由 spring-ai-mcp-client auto-config 注入 */
    private final ToolCallbackProvider mcpToolProvider;
    private final QueryRewriter queryRewriter;
    private final RagCategoryInferrer categoryInferrer;
    private final RagAdvisorFactory ragAdvisorFactory;
    /** ChatMemory — 查询历史对话用于前端刷新恢复 */
    private final ChatMemory chatMemory;
    /** RedissonClient — Manus 历史持久化（独立于 ChatMemory 的存储 key） */
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static final String MANUS_HISTORY_PREFIX = "manus:history:";

    public NongpiAssistantApp(ChatClient chatClient,
                              NongpiToolSet nongpiToolSet,
                              @Autowired(required = false) ToolCallbackProvider mcpToolProvider,
                              QueryRewriter queryRewriter,
                              RagCategoryInferrer categoryInferrer,
                              RagAdvisorFactory ragAdvisorFactory,
                              ChatMemory chatMemory,
                              RedissonClient redissonClient,
                              ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.nongpiToolSet = nongpiToolSet;
        this.mcpToolProvider = mcpToolProvider;
        this.queryRewriter = queryRewriter;
        this.categoryInferrer = categoryInferrer;
        this.ragAdvisorFactory = ragAdvisorFactory;
        this.chatMemory = chatMemory;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper.copy();
    }

    /**
     * 查询历史对话 — 供前端刷新页面后恢复对话记录
     * <p>直接调 ChatMemory.get() 从 Redis 拉取。若 Redis key 已过期（TTL），
     * 返回空列表 → 前端据此清 localStorage 重新开 chatId。</p>
     * <p>异常容错：Redis 抖动等异常时返回空列表（而非抛 500），避免前端误判为"会话过期"
     * 清掉 localStorage chatId。真正的会话过期由 Redis TTL 自然触发。</p>
     */
    public List<ChatMessageDto> getHistory(String chatId) {
        try {
            List<Message> messages = chatMemory.get(chatId);
            List<ChatMessageDto> result = new ArrayList<>();
            for (Message msg : messages) {
                result.add(new ChatMessageDto(msg.getMessageType().name(), msg.getText()));
            }
            return result;
        } catch (Exception e) {
            log.warn("ChatMemory getHistory 失败 chatId={}: {}", chatId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 查询 Manus 对话历史 — 供前端刷新页面后恢复智能体对话记录
     * <p>Manus 历史存 Redis key {@code manus:history:<chatId>}，TTL 1h。
     * 只存 USER 原始消息 + ASSISTANT 最终回复，不存 ReAct 步骤。</p>
     */
    public List<ChatMessageDto> getManusHistory(String chatId) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(MANUS_HISTORY_PREFIX + chatId, StringCodec.INSTANCE);
            String json = bucket.get();
            if (json == null) return new ArrayList<>();
            List<Map<String, String>> dtoList = objectMapper.readValue(json, new TypeReference<>() {});
            List<ChatMessageDto> result = new ArrayList<>();
            for (Map<String, String> dto : dtoList) {
                result.add(new ChatMessageDto(dto.get("role"), dto.get("content")));
            }
            return result;
        } catch (Exception e) {
            log.warn("Manus history 查询失败 chatId={}: {}", chatId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 前端历史消息 DTO — 只需 role + content，不需要 metadata */
    public record ChatMessageDto(String role, String content) {}

    /**
     * 基础对话（同步，含记忆 + 工具）
     * <p>工具在此显式挂载（.tools），而非 bean 级 defaultTools —
     * .call() 自动执行工具调用循环。MCP 工具（weather/filesystem）通过
     * ToolCallbackProvider 一并挂载，LLM 按需调用。</p>
     */
    public String chat(String message, String chatId) {
        var prompt = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .tools(nongpiToolSet);
        if (mcpToolProvider != null) {
            prompt = prompt.toolCallbacks(mcpToolProvider);
        }
        return prompt.call().content();
    }

    /**
     * 流式对话（SSE，前端逐字接收）
     * <p>Spring AI 1.1.2 的 .stream() 在 servlet 环境（无完整 reactor-netty）
     * 下 Flux 订阅后静默不 emit（onSubscribe 但无 onNext），导致前端无限等待。
     * fallback：异步线程同步 .call() 获取完整响应，再按 chunk 拆分推送模拟流式。
     * 虚拟线程（spring.threads.virtual.enabled=true）承载阻塞调用，不占平台线程。</p>
     */
    public SseEmitter chatStream(String message, String chatId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        CompletableFuture.runAsync(() -> {
            try {
                SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
                var prompt = chatClient.prompt()
                        .user(message)
                        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                        .tools(nongpiToolSet);
                if (mcpToolProvider != null) {
                    prompt = prompt.toolCallbacks(mcpToolProvider);
                }
                String content = prompt.call().content();
                streamSimulated(content, emitter);
            } catch (Exception e) {
                try { emitter.send(SseEmitter.event().data("生成失败: " + e.getMessage())); } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } finally {
                // 兜底 complete — 若 emitter.send 已抛异常（前端断开），
                // completeWithError 不会执行，emitter 永不关闭导致 Tomcat 异步请求泄漏
                SecurityContextHolder.clearContext();
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * RAG 对话 — 检索知识库后回答（用于业务知识问答，如"常温区干货能放多久"）
     * <p>出库流程：QueryRewriter 重写查询 → 推断 category → 带 filterExpression 的 RAG advisor 检索 → 回答</p>
     * <p>流式版本：与 chatStream 一致用 SseEmitter，fallback 模拟流式。</p>
     */
    public SseEmitter chatWithRagStream(String message, String chatId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        CompletableFuture.runAsync(() -> {
            try {
                SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
                String rewritten = queryRewriter.rewrite(message);
                String category = categoryInferrer.inferCategory(message);
                RetrievalAugmentationAdvisor ragAdvisor = ragAdvisorFactory.create(category);
                String content = chatClient.prompt()
                        .user(rewritten)
                        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                        .advisors(ragAdvisor)
                        .call()
                        .content();
                streamSimulated(content, emitter);
            } catch (Exception e) {
                try { emitter.send(SseEmitter.event().data("生成失败: " + e.getMessage())); } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } finally {
                SecurityContextHolder.clearContext();
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * 把同步获取的完整文本按固定长度拆分，逐段推给 SseEmitter 模拟流式打字效果。
     * 前端体验与真流式一致（逐字出现），区别仅在于 LLM 先生成完整响应再拆分推送。
     */
    private void streamSimulated(String content, SseEmitter emitter) {
        if (content == null || content.isEmpty()) {
            emitter.complete();
            return;
        }
        try {
            for (int i = 0; i < content.length(); i += CHUNK_SIZE) {
                int end = Math.min(i + CHUNK_SIZE, content.length());
                emitter.send(SseEmitter.event().data(content.substring(i, end)));
                Thread.sleep(CHUNK_DELAY_MS);
            }
            emitter.complete();
        } catch (IOException | InterruptedException e) {
            emitter.completeWithError(e);
        }
    }
}
