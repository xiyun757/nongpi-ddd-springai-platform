package com.nongpi.fulfillment.ai.agent;

import com.nongpi.fulfillment.ai.agent.model.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具调用智能体 — ReAct think/act 的具体实现
 * <p>参考 yu-ai-agent ToolCallAgent 设计：
 * <ul>
 *   <li>think()：构建 Prompt（历史上下文 + 工具列表 + 禁用内置执行）调 LLM，拿到 tool_call</li>
 *   <li>act()：手动执行 LLM 选中的工具，更新多轮上下文，检测 doTerminate 终止</li>
 * </ul>
 * 禁用 Spring AI 内置工具执行（internalToolExecutionEnabled=false），让 ReAct 手动控制每一步。</p>
 */
public abstract class ToolCallAgent extends ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ToolCallAgent.class);

    /** 可用工具回调（业务工具 + TerminateTool） */
    protected ToolCallback[] availableTools;
    /** LLM 模型 */
    protected ChatModel chatModel;
    /** 系统提示词（角色设定 + 工具使用指引） */
    protected String systemPrompt;
    /** 下一步提示词（引导 LLM 拆解任务、用 terminate 结束） */
    protected String nextStepPrompt;

    /** 多轮上下文消息列表 */
    protected final List<Message> messageList = new ArrayList<>();
    /** think() 暂存的 LLM 响应，供 act() 取 tool_call */
    protected ChatResponse currentResponse;

    public void setAvailableTools(ToolCallback[] availableTools) { this.availableTools = availableTools; }
    public void setChatModel(ChatModel chatModel) { this.chatModel = chatModel; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public void setNextStepPrompt(String nextStepPrompt) { this.nextStepPrompt = nextStepPrompt; }

    @Override
    protected void initialize(String message) {
        messageList.clear();
        messageList.add(new SystemMessage(systemPrompt));
        messageList.add(new UserMessage(message));
    }

    /**
     * 思考：调 LLM 选择下一步工具
     * @return true 表示 LLM 发起工具调用；false 表示已给出最终回复
     */
    @Override
    protected boolean think() {
        // 不吞异常 — 让 LLM 调用异常自然向上抛，由 BaseAgent.runStream 的 catch
        // 统一通过 SSE 发送 error 事件。原 try-catch 把 state 设为 ERROR 又 return false，
        // 会被 ReActAgent.step() 覆盖为 FINISHED，前端永远看不到错误。
        messageList.add(new UserMessage(nextStepPrompt));
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .toolCallbacks(availableTools)
                .internalToolExecutionEnabled(false)
                .build();
        Prompt prompt = new Prompt(messageList, options);
        currentResponse = chatModel.call(prompt);
        AssistantMessage assistantMsg = currentResponse.getResult().getOutput();
        messageList.add(assistantMsg);
        boolean hasToolCall = assistantMsg.hasToolCalls();
        log.info("[{}] think: {}", name, hasToolCall
                ? "选择工具 " + assistantMsg.getToolCalls()
                : "给出最终回复");
        return hasToolCall;
    }

    /**
     * 行动：手动执行 think 选中的工具，更新上下文，检测终止
     * @return 本步执行结果描述
     */
    @Override
    protected String act() {
        AssistantMessage assistantMsg = currentResponse.getResult().getOutput();
        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
        boolean terminated = false;
        List<String> resultParts = new ArrayList<>();

        for (AssistantMessage.ToolCall toolCall : assistantMsg.getToolCalls()) {
            String toolName = toolCall.name();
            String args = toolCall.arguments();
            if ("doTerminate".equals(toolName)) {
                terminated = true;
                resultParts.add("任务完成信号");
                continue;
            }
            // 在可用工具中按名查找并执行
            String toolResult = null;
            for (ToolCallback cb : availableTools) {
                if (cb.getToolDefinition().name().equals(toolName)) {
                    try {
                        toolResult = cb.call(args);
                    } catch (Exception e) {
                        toolResult = "工具执行异常：" + e.getMessage();
                        log.warn("[{}] 工具 {} 执行异常：{}", name, toolName, e.getMessage());
                    }
                    break;
                }
            }
            if (toolResult == null) {
                toolResult = "未找到工具：" + toolName;
            }
            toolResponses.add(new ToolResponseMessage.ToolResponse(
                    toolCall.id(), toolName, toolResult));
            resultParts.add(toolName + " → " + truncate(toolResult, 200));
            log.info("[{}] act: 调用 {} 参数 {}", name, toolName, args);
        }

        // 把工具结果加入上下文（多轮记忆的关键）
        if (!toolResponses.isEmpty()) {
            messageList.add(ToolResponseMessage.builder()
                    .responses(toolResponses)
                    .build());
        }
        if (terminated) {
            state = AgentState.FINISHED;
        }
        return String.join("；", resultParts);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
