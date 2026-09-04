package com.nongpi.fulfillment.ai.api;

import com.nongpi.fulfillment.ai.agent.NongpiManus;
import com.nongpi.fulfillment.ai.app.NongpiAssistantApp;
import com.nongpi.fulfillment.ai.app.NongpiAssistantApp.ChatMessageDto;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.ApplicationContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 智能客服接口入口
 * <p>
 * 端点清单：
 * <ul>
 *   <li>POST /chat — 基础对话（记忆+工具）</li>
 *   <li>POST /chat/stream — 流式对话 SSE（SseEmitter，与 /manus/chat 一致）</li>
 *   <li>POST /chat/rag — RAG 知识库问答</li>
 *   <li>GET /manus/chat — Manus ReAct 多步执行 SSE</li>
 * </ul>
 * </p>
 */
@RestController
@Validated
@RequestMapping("/api/ai")
public class AiController {

    private final NongpiAssistantApp assistant;
    private final ApplicationContext applicationContext;

    public AiController(NongpiAssistantApp assistant, ApplicationContext applicationContext) {
        this.assistant = assistant;
        this.applicationContext = applicationContext;
    }

    /**
     * 基础对话（同步）
     */
    @PostMapping("/chat")
    public String chat(@RequestParam String message,
                       @Pattern(regexp = "^[A-Za-z0-9_:-]{1,128}$", message = "chatId 只允许字母数字下划线冒号连字符")
                       @RequestParam String chatId) {
        validateChatId(chatId);
        return assistant.chat(message, chatId);
    }

    /**
     * 流式对话（SSE，前端逐字接收）— 与 /manus/chat 一致使用 SseEmitter
     * <p>GET + query 参数，避免 POST 无 body 的 Content-Type 歧义</p>
     */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestParam String message,
                                 @Pattern(regexp = "^[A-Za-z0-9_:-]{1,128}$", message = "chatId 只允许字母数字下划线冒号连字符")
                                 @RequestParam String chatId) {
        validateChatId(chatId);
        return assistant.chatStream(message, chatId);
    }

    /**
     * RAG 对话 — 检索知识库后流式回答（业务知识问答，如冷链存储规范）
     * <p>GET + query 参数，与 /chat/stream 和 /manus/chat 一致用 SseEmitter 流式推送。</p>
     */
    @GetMapping(value = "/chat/rag", produces = "text/event-stream")
    public SseEmitter chatWithRag(@RequestParam String message,
                                  @Pattern(regexp = "^[A-Za-z0-9_:-]{1,128}$", message = "chatId 只允许字母数字下划线冒号连字符")
                                  @RequestParam String chatId) {
        validateChatId(chatId);
        return assistant.chatWithRagStream(message, chatId);
    }

    /**
     * Manus ReAct 智能体 — 多步规划+执行，SSE 逐步推送 think/act 过程
     * <p>示例：查批次 LOT001 的状态并入库补充</p>
     * <p>chatId 用于持久化对话历史到 Redis（key=manus:history:{chatId}，TTL 1h），
     * 刷新页面后前端调 /manus/history 恢复对话记录。ReAct 步骤不持久化，只存最终对话。</p>
     * <p>NongpiManus 是 prototype scope，每次 getBean 取新实例避免并发串话。</p>
     */
    @GetMapping(value = "/manus/chat", produces = "text/event-stream")
    public SseEmitter manusChat(@RequestParam String message,
                                @Pattern(regexp = "^[A-Za-z0-9_:-]{1,128}$", message = "chatId 只允许字母数字下划线冒号连字符")
                                @RequestParam String chatId) {
        validateChatId(chatId);
        NongpiManus nongpiManus = applicationContext.getBean(NongpiManus.class);
        nongpiManus.setChatId(chatId);
        return nongpiManus.runStream(message);
    }

    /**
     * 查询 Manus 对话历史 — 供前端刷新页面后恢复智能体对话记录
     * <p>从 Redis 拉 manus:history:{chatId}，只含 USER/ASSISTANT 消息（无 ReAct 步骤）。</p>
     */
    @GetMapping("/manus/history")
    public List<ChatMessageDto> manusHistory(@RequestParam String chatId) {
        validateChatId(chatId);
        return assistant.getManusHistory(chatId);
    }

    /**
     * 查询历史对话 — 供前端刷新页面后恢复对话记录
     * <p>从 Redis ChatMemory 拉取指定 chatId 的历史消息。若 Redis key 已过期（TTL 1h），
     * 返回空列表 → 前端据此清 localStorage 重新开 chatId。</p>
     */
    @GetMapping("/history")
    public List<ChatMessageDto> history(@RequestParam String chatId) {
        validateChatId(chatId);
        return assistant.getHistory(chatId);
    }

    /**
     * 校验 chatId 非空 — 避免前端传空导致多用户共享同一个 ChatMemory 会话
     */
    private void validateChatId(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }
    }
}
