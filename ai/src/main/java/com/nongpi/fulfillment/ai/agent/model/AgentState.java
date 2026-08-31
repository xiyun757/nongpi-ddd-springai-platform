package com.nongpi.fulfillment.ai.agent.model;

/**
 * ReAct 智能体状态枚举
 * <p>参考 yu-ai-agent AgentState 设计。</p>
 */
public enum AgentState {
    /** 运行中，think/act 循环进行 */
    RUNNING,
    /** 任务完成（AI 调用 doTerminate）或达到最大步数 */
    FINISHED,
    /** 执行出错 */
    ERROR
}
