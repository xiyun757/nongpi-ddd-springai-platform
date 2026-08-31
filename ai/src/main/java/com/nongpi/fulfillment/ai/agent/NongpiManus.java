package com.nongpi.fulfillment.ai.agent;

import com.nongpi.fulfillment.ai.tools.NongpiToolSet;
import com.nongpi.fulfillment.ai.tools.TerminateTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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

    private static final String SYSTEM_PROMPT = """
            你是农批履约中台的智能执行体（Manus）。
            你可以多步规划并执行复杂任务，可用工具：
            - 查询类：queryLotStatus（批次详情）、queryInventory（库存详情）、listAlertRecords（预警记录）
            - 写操作：inboundLot（入库）、outboundLot（出库 FEFO）、freezeStock（冻结库存）、checkExpiringLots（跨域触发临期扫描）
            - 终止：doTerminate（任务完成时调用）
            写操作需要 ADMIN 权限。每一步只选择一个工具，根据结果决定下一步。
            遇到错误如实反馈，不要重试失败的操作。任务全部完成后调用 doTerminate。
            """;

    private static final String NEXT_STEP_PROMPT = """
            请根据当前任务进展，选择下一步要执行的工具。
            如果任务已全部完成，请调用 doTerminate。
            如果已有足够信息可以回答用户，直接给出最终回复（不调工具）。
            """;

    public NongpiManus(NongpiToolSet toolSet, TerminateTool terminateTool, ChatModel chatModel) {
        setName("nongpiManus");
        setMaxSteps(20);
        setChatModel(chatModel);
        setSystemPrompt(SYSTEM_PROMPT);
        setNextStepPrompt(NEXT_STEP_PROMPT);
        // 业务工具 + 终止工具 → ToolCallback[]
        setAvailableTools(ToolCallbacks.from(toolSet, terminateTool));
    }
}
