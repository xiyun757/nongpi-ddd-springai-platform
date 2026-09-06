package com.nongpi.fulfillment.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.fulfillment.ai.tools.NongpiToolSet;
import com.nongpi.fulfillment.ai.tools.TerminateTool;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 农批智能体 — ReAct Manus 的具体配置
 * <p>参考 yu-ai-agent YuManus 设计。装配业务工具（NongpiToolSet）+ 终止标记（TerminateTool），
 * 设置系统提示词与下一步指引，maxSteps=20 防止无限循环。</p>
 * <p>使用方式：{@code nongpiManus.runStream(message)} 返回 SseEmitter，前端逐步接收 think/act 过程。</p>
 * <p>@Scope("prototype") — 每次请求新实例，避免 messageList 实例字段被多用户并发串话。
 * Controller 通过 ApplicationContext.getBean(NongpiManus.class) 获取新实例。</p>
 */
@Component
@Scope("prototype")
public class NongpiManus extends ToolCallAgent {

    private static final Logger log = LoggerFactory.getLogger(NongpiManus.class);
    private static final String HISTORY_KEY_PREFIX = "manus:history:";
    private static final Duration HISTORY_TTL = Duration.ofHours(1);

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是农批履约中台的智能执行体（Manus）。
            你可以多步规划并执行复杂任务，可用工具：
            - 查询类：listSkuCatalog（商品目录）、queryLotStatus（批次详情）、queryInventory（库存详情）、listAlertRecords（预警记录）
            - 写操作：inboundLot（入库）、outboundLot（出库 FEFO）、transferLot（转库）、freezeStock（冻结库存）、checkExpiringLots（跨域触发临期扫描）
            - 终止：doTerminate（任务完成时调用）
            当用户提到商品名而非 skuId 时，先调用 listSkuCatalog 查出对应 skuId，再执行写操作。
            写操作需要 ADMIN 权限。每一步只选择一个工具，根据结果决定下一步。
            遇到错误如实反馈，不要重试失败的操作。任务全部完成后调用 doTerminate。
            """;

    private static final String NEXT_STEP_PROMPT = """
            请根据当前任务进展，选择下一步要执行的工具。
            如果任务已全部完成，请调用 doTerminate。
            如果已有足够信息可以回答用户，直接给出最终回复（不调工具）。
            """;

    public NongpiManus(NongpiToolSet toolSet, TerminateTool terminateTool, ChatModel chatModel,
                       RedissonClient redissonClient, ObjectMapper objectMapper) {
        setName("nongpiManus");
        setMaxSteps(20);
        setChatModel(chatModel);
        setSystemPrompt(SYSTEM_PROMPT);
        setNextStepPrompt(NEXT_STEP_PROMPT);
        // 业务工具 + 终止工具 → ToolCallback[]
        setAvailableTools(ToolCallbacks.from(toolSet, terminateTool));
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper.copy();
    }

    /**
     * 任务完成后持久化对话历史到 Redis — 存完整历史：USER 消息 + 每步 STEP + ASSISTANT 最终回复
     * <p>演示项目需要完整展示 ReAct 过程，step 步骤一并持久化。</p>
     * <p>追加模式：同一 chatId 多次执行追加到已有历史列表，不覆盖。TTL 1h 自动过期。</p>
     */
    @Override
    protected void onCompleted() {
        if (chatId == null || messageList.isEmpty()) return;
        try {
            // 提取用户原始消息（messageList[1]，跳过 SystemMessage）
            String userMessage = "";
            if (messageList.size() > 1 && messageList.get(1) instanceof UserMessage) {
                userMessage = messageList.get(1).getText();
            }
            // 提取最后一条有文本内容的 AssistantMessage
            String assistantMessage = "";
            for (int i = messageList.size() - 1; i >= 0; i--) {
                Message msg = messageList.get(i);
                if (msg instanceof AssistantMessage && !msg.getText().isBlank()) {
                    assistantMessage = msg.getText();
                    break;
                }
            }
            if (assistantMessage.isBlank()) {
                assistantMessage = "Manus 任务执行完成（无文本回复）";
            }
            // 读取已有历史，追加新消息（同一 chatId 多次执行）
            RBucket<String> bucket = redissonClient.getBucket(HISTORY_KEY_PREFIX + chatId, StringCodec.INSTANCE);
            String existing = bucket.get();
            List<Map<String, String>> dtoList;
            if (existing != null) {
                dtoList = objectMapper.readValue(existing, new TypeReference<>() {});
            } else {
                dtoList = new ArrayList<>();
            }
            // 追加完整对话：USER → 每步 STEP → ASSISTANT
            dtoList.add(Map.of("role", "USER", "content", userMessage));
            for (String step : stepHistory) {
                dtoList.add(Map.of("role", "STEP", "content", step));
            }
            dtoList.add(Map.of("role", "ASSISTANT", "content", assistantMessage));
            String json = objectMapper.writeValueAsString(dtoList);
            bucket.set(json, HISTORY_TTL);
            log.info("[{}] Manus 历史已持久化 chatId={}, 共{}条消息", name, chatId, dtoList.size());
        } catch (Exception e) {
            log.warn("[{}] Manus 历史持久化失败 chatId={}: {}", name, chatId, e.getMessage());
        }
    }
}
