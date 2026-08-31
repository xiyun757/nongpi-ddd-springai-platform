package com.nongpi.fulfillment.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * 查询重写器 — 调 LLM 将用户口语化问题重写为更适合向量检索的查询
 * <p>例如 "我和女朋友吵架了" → "恋爱中情侣吵架后的沟通方法和矛盾化解策略"。
 * 提升检索召回率，参考 yu-ai-agent QueryRewriter 设计。</p>
 */
@Component
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);
    private static final String REWRITE_PROMPT = """
            你是检索查询重写专家。请将以下用户问题重写为更适合向量检索的查询语句：
            1. 用更精确、更通用的术语替换口语化表达
            2. 补充隐含的检索关键词
            3. 只输出重写后的查询语句，不要解释、不要引号

            用户问题：%s
            """;

    private final ChatModel chatModel;

    public QueryRewriter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 重写用户查询；LLM 失败时降级返回原查询
     */
    public String rewrite(String userQuery) {
        try {
            String rewritten = chatModel.call(new Prompt(
                    String.format(REWRITE_PROMPT, userQuery)))
                    .getResult().getOutput().getText();
            String result = rewritten == null ? userQuery : rewritten.trim();
            log.info("查询重写：[{}] → [{}]", userQuery, result);
            return result;
        } catch (Exception e) {
            log.warn("查询重写失败，使用原查询：{}", e.getMessage());
            return userQuery;
        }
    }
}
