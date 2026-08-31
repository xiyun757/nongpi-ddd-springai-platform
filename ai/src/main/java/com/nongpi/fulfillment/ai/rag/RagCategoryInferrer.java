package com.nongpi.fulfillment.ai.rag;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 类别推断器 — 根据用户问题关键词推断知识库 category，用于 RAG filterExpression 精准检索
 * <p>参考 yu-ai-agent inferStatus 设计。农批知识库分三类：
 * storage-spec（存储规范）、lot-mgmt（批次管理）、alert-rule（预警规则）。</p>
 */
@Component
public class RagCategoryInferrer {

    /** category → 触发关键词 */
    private static final Map<String, String[]> CATEGORY_KEYWORDS = Map.of(
            "storage-spec", new String[]{"存储", "温区", "冷冻", "冷藏", "常温", "湿度", "保质期", "混放"},
            "lot-mgmt", new String[]{"批次", "入库", "出库", "转库", "FEFO", "流转", "状态机", "调拨"},
            "alert-rule", new String[]{"预警", "临期", "过期", "规则", "阈值", "补货", "安全库存"}
    );

    /**
     * 推断用户问题所属 category；无法匹配返回 null（检索时不过滤）
     */
    public String inferCategory(String userQuery) {
        if (userQuery == null) return null;
        String lower = userQuery.toLowerCase();
        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
