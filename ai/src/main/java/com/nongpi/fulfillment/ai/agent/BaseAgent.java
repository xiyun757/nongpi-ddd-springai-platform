package com.nongpi.fulfillment.ai.agent;

import com.nongpi.fulfillment.ai.agent.model.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * ReAct 智能体基类 — 循环框架 + 状态管理 + SSE 输出
 * <p>参考 yu-ai-agent BaseAgent 设计。子类实现 {@link #step()} 返回每步结果字符串。
 * runStream 创建 SseEmitter，异步线程跑循环，达到 {@link #maxSteps} 或 FINISHED 退出。</p>
 */
public abstract class BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(BaseAgent.class);

    /** 智能体名称 */
    protected String name;
    /** 最大执行步数，防止无限循环 */
    protected int maxSteps = 20;
    /** 当前状态 */
    protected AgentState state = AgentState.RUNNING;
    /** 会话 ID — 用于任务完成后持久化历史到 Redis（manus 模式） */
    protected String chatId;
    /** 每步执行结果收集 — 供 onCompleted 持久化完整历史（含 step） */
    protected final java.util.List<String> stepHistory = new java.util.ArrayList<>();

    public void setName(String name) { this.name = name; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    /**
     * 单步执行：子类实现（ReActAgent 拆分为 think + act）
     * @return 本步结果字符串，推给 SSE 前端
     */
    protected abstract String step();

    /**
     * 流式运行 — 返回 SseEmitter，异步执行循环
     * <p>传播主线程 SecurityContext 到异步线程，工具调用能拿到 JWT 认证角色。
     * CompletableFuture.runAsync 用 ForkJoinPool，SecurityContextHolder 是 ThreadLocal，
     * 默认不跨线程传播 → 工具里 requireAdmin() 拿到 null auth → 403。</p>
     */
    public SseEmitter runStream(String message) {
        // 捕获主线程的 Authentication，新建独立 SecurityContext 传播到异步线程。
        // 不能直接捕获 SecurityContextHolder.getContext() 引用 — Spring Security 在请求结束
        // 时会 clearContext() 把同一个对象清空，异步线程拿到的是空 context。
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 5 分钟超时，足够多步任务完成
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        this.state = AgentState.RUNNING;
        int[] stepCounter = {0};

        CompletableFuture.runAsync(() -> {
            // 用捕获的 authentication 新建独立 SecurityContext，避免被主线程 clearContext 清空
            if (authentication != null) {
                SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
            }
            try {
                initialize(message);
                while (state == AgentState.RUNNING && stepCounter[0] < maxSteps) {
                    stepCounter[0]++;
                    log.info("[{}] 执行第 {} 步", name, stepCounter[0]);
                    String stepResult = step();
                    if (stepResult != null && !stepResult.isBlank()) {
                        String stepMsg = "Step " + stepCounter[0] + ": " + stepResult;
                        stepHistory.add(stepMsg);
                        emitter.send(SseEmitter.event()
                                .name("step")
                                .data(stepMsg));
                    }
                }
                if (state == AgentState.RUNNING) {
                    log.warn("[{}] 达到最大步数 {}，强制结束", name, maxSteps);
                    state = AgentState.FINISHED;
                }
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("Manus 任务结束，共执行 " + stepCounter[0] + " 步"));
                emitter.complete();
            } catch (Exception e) {
                log.error("[{}] 执行异常：{}", name, e.getMessage(), e);
                state = AgentState.ERROR;
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("执行异常：" + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } finally {
                // 任务完成后持久化历史（manus 模式存 Redis）
                if (chatId != null) {
                    try { onCompleted(); } catch (Exception ignored) {}
                }
                cleanup();
                // SseEmitter.complete() 幂等，已完成的会静默 return。
                // finally 兜底确保即使 completeWithError 内部抛异常（如 emitter.send 抛 IOException 后
                // completeWithError 又失败），emitter 也不会永久泄漏挂起 Tomcat 异步请求。
                try { emitter.complete(); } catch (Exception ignored) {}
                // 清理异步线程的 SecurityContext，避免线程池复用导致上下文泄漏
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    /**
     * 初始化上下文：加入用户初始消息
     */
    protected abstract void initialize(String message);

    /**
     * 任务完成后的回调钩子 — 子类可重写以持久化历史（如 manus 存 Redis）
     * 在 finally 块中调用，此时 messageList 已包含完整对话上下文。
     */
    protected void onCompleted() { }

    /**
     * 清理资源
     */
    protected void cleanup() { }
}
