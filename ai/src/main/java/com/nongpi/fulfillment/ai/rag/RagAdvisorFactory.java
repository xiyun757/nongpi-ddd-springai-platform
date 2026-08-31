package com.nongpi.fulfillment.ai.rag;

import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

/**
 * RAG Advisor 工厂 — 按推断的 category 动态生成带 filterExpression 的检索增强顾问
 * <p>参考 yu-ai-agent LoveAppRagCustomAdvisorFactory 设计。
 * category 为 null 时不加过滤条件，全库检索。</p>
 */
@Component
public class RagAdvisorFactory {

    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final int TOP_K = 3;

    private final VectorStore vectorStore;

    public RagAdvisorFactory(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 创建 RAG advisor；category 为 null 时全库检索
     */
    public RetrievalAugmentationAdvisor create(String category) {
        VectorStoreDocumentRetriever.Builder retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .topK(TOP_K);
        if (category != null) {
            Filter.Expression expression = new FilterExpressionBuilder()
                    .eq("category", category).build();
            retrieverBuilder.filterExpression(expression);
        }
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retrieverBuilder.build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();
    }
}
