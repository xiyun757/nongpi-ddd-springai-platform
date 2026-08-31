package com.nongpi.fulfillment.ai.agent;

/**
 * ReAct 智能体抽象层 — 把每一步拆分为 think（思考选工具）+ act（执行工具）
 * <p>参考 yu-ai-agent ReActAgent 设计。step() 顺序调用 think() 与 act()，
 * think 返回 false（无工具调用）表示 LLM 已给出最终答案，直接结束。</p>
 */
public abstract class ReActAgent extends BaseAgent {

    /**
     * 思考：调 LLM 选择下一步要执行的工具
     * @return true 表示 LLM 发起了工具调用；false 表示 LLM 已给出最终回复（无工具调用）
     */
    protected abstract boolean think();

    /**
     * 行动：执行 think 选中的工具，更新多轮上下文，检测是否调用 doTerminate
     * @return 本步执行结果描述
     */
    protected abstract String act();

    @Override
    protected String step() {
        boolean hasToolCall = think();
        if (!hasToolCall) {
            // LLM 未调工具，说明已给出最终答案，直接结束
            state = com.nongpi.fulfillment.ai.agent.model.AgentState.FINISHED;
            return "LLM 已给出最终回复，任务结束";
        }
        return act();
    }
}
