package com.nongpi.fulfillment.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 终止标记工具 — ReAct 智能体调用此工具表示任务完成，触发循环退出
 * <p>参考 yu-ai-agent TerminateTool 设计。
 * 在单轮对话（ChatClient）中调用无影响；在 Manus 多轮循环中调用后 act() 检测到 → setState(FINISHED)。</p>
 */
@Component
public class TerminateTool {

    @Tool(description = "当任务已全部完成时调用此工具，表示无需继续操作。仅在确认所有步骤执行完毕后调用。")
    public String doTerminate() {
        return "Task completed";
    }
}
