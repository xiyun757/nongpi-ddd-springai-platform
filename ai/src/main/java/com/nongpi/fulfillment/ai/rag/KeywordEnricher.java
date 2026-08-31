package com.nongpi.fulfillment.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关键词增强器 — 调 LLM 为每个文档分片生成 3-5 个关键词，写入 metadata.keywords
 * <p>关键词提升向量检索召回率：用户问题与分片关键词匹配时，相似度更高。
 * 参考 yu-ai-agent MyKeywordEnricher 设计。</p>
 */
@Component
public class KeywordEnricher {

    private static final Logger log = LoggerFactory.getLogger(KeywordEnricher.class);
    private static final String ENRICH_PROMPT = """
            请为以下文本生成 3 到 5 个关键词，用逗号分隔，只输出关键词本身，不要解释：
            %s
            """;

    private final ChatModel chatModel;

    public KeywordEnricher(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 对分片列表逐个生成关键词并写入 metadata
     */
    public List<Document> enrichDocuments(List<Document> splitDocuments) {
        List<Document> enriched = new ArrayList<>();
        int count = 0;
        for (Document doc : splitDocuments) {
            try {
                String keywords = chatModel.call(new Prompt(
                        String.format(ENRICH_PROMPT, truncate(doc.getText(), 800))))
                        .getResult().getOutput().getText();
                Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                metadata.put("keywords", keywords == null ? "" : keywords.trim());
                Document enrichedDoc = new Document(doc.getId(), doc.getText(), metadata);
                enriched.add(enrichedDoc);
                count++;
            } catch (Exception e) {
                // LLM 调用失败时保留原分片，不阻断入库
                log.warn("文档 [{}] 关键词增强失败：{}", doc.getMetadata().get("source"), e.getMessage());
                enriched.add(doc);
            }
        }
        log.info("关键词增强完成：{}/{} 个分片", count, splitDocuments.size());
        return enriched;
    }

    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
