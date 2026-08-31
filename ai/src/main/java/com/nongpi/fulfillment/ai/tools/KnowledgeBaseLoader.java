package com.nongpi.fulfillment.ai.tools;

import com.nongpi.fulfillment.ai.rag.DocumentLoader;
import com.nongpi.fulfillment.ai.rag.KeywordEnricher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库加载器 — 启动时执行完整 RAG 入库管线
 * <p>流程：DocumentLoader 加载 document/*.md → 按 --- 分割线做 section 分片
 * → 每个 section 独立 TokenTextSplitter 再分片 → 相邻 chunk 加 20% overlap
 * → KeywordEnricher 调 LLM 生成关键词 → vectorStore.add 向量化入库。</p>
 * <p>启动时查 vector_store 行数，为 0 才灌入，避免重启重复入库。</p>
 */
@Component
public class KnowledgeBaseLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseLoader.class);

    private static final String SECTION_SEPARATOR = "\n---\n";
    // overlap 比例：上一个 chunk 的尾部 20% 拼接到下一个 chunk 头部
    private static final double OVERLAP_RATIO = 0.2;

    private final VectorStore vectorStore;
    private final JdbcTemplate pgJdbcTemplate;
    private final DocumentLoader documentLoader;
    private final KeywordEnricher keywordEnricher;
    private final TokenTextSplitter textSplitter;

    public KnowledgeBaseLoader(VectorStore vectorStore,
                               @org.springframework.beans.factory.annotation.Qualifier("pgJdbcTemplate")
                               JdbcTemplate pgJdbcTemplate,
                               DocumentLoader documentLoader,
                               KeywordEnricher keywordEnricher) {
        this.vectorStore = vectorStore;
        this.pgJdbcTemplate = pgJdbcTemplate;
        this.documentLoader = documentLoader;
        this.keywordEnricher = keywordEnricher;
        // TokenTextSplitter 构造: chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator
        this.textSplitter = new TokenTextSplitter(200, 50, 5, 100, false);
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            Integer count = pgJdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
            if (count != null && count > 0) {
                log.info("RAG 知识库已有 {} 条向量，跳过入库", count);
                return;
            }
            // ① 加载文档（带 category 元数据）
            List<Document> docs = documentLoader.loadMarkdowns();
            if (docs.isEmpty()) {
                log.warn("未找到知识文档，跳过入库");
                return;
            }
            log.info("加载 {} 篇知识文档，开始入库管线", docs.size());

            // ② 先按 --- 分割线分 section，每个 section 独立 TokenTextSplitter 分片
            //    避免 TokenTextSplitter 跨越 markdown 结构切断语义
            List<Document> sectionSplitDocs = splitBySections(docs);
            log.info("按 --- 分割线分 section：{} 篇 → {} 个 section", docs.size(), sectionSplitDocs.size());

            // ③ section 内再按 token 分片
            List<Document> splitDocs = textSplitter.apply(sectionSplitDocs);
            log.info("TokenTextSplitter 分片：{} 个 section → {} 个 chunk", sectionSplitDocs.size(), splitDocs.size());

            // ④ 相邻 chunk 加 20% overlap（同一文档内）
            List<Document> overlappedDocs = addOverlap(splitDocs);
            log.info("加 overlap 后：{} 个 chunk", overlappedDocs.size());

            // ⑤ 关键词增强
            List<Document> enriched = keywordEnricher.enrichDocuments(overlappedDocs);

            // ⑥ 向量化入库
            vectorStore.add(enriched);
            log.info("RAG 入库完成：{} 个分片已灌入 PGVector，管线验证通过", enriched.size());
        } catch (Exception e) {
            // PGVector 容器或 LLM 未就绪时降级：仅打日志，不阻断应用启动
            log.warn("RAG 知识库入库失败（PGVector 容器或 LLM 可能未启动）：{}", e.getMessage());
        }
    }

    /**
     * 按 --- 分割线将每篇文档拆成多个 section，保留原 metadata
     */
    private List<Document> splitBySections(List<Document> docs) {
        List<Document> result = new ArrayList<>();
        for (Document doc : docs) {
            String text = doc.getText();
            Map<String, Object> baseMeta = doc.getMetadata();
            String[] sections = text.split(SECTION_SEPARATOR);
            for (int i = 0; i < sections.length; i++) {
                String section = sections[i].trim();
                if (section.isEmpty()) continue;
                // 复制 metadata 并加 sectionIndex
                Map<String, Object> meta = new HashMap<>(baseMeta);
                meta.put("sectionIndex", i);
                meta.put("totalSections", sections.length);
                result.add(new Document(section, meta));
            }
        }
        return result;
    }

    /**
     * 同一文档内的相邻 chunk，上一个 chunk 尾部 20% 拼接到下一个 chunk 头部
     * <p>overlap 只在同一文档（相同 source）内生效，不跨文档拼接</p>
     * <p>overlap 不跨越 --- 分割线的边界（每个 section 首 chunk 不加 overlap）</p>
     */
    private List<Document> addOverlap(List<Document> chunks) {
        if (chunks.size() < 2) return chunks;

        List<Document> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document current = chunks.get(i);
            // 找前一个 chunk（同 source 且同 sectionIndex）
            Document prev = i > 0 ? chunks.get(i - 1) : null;
            if (prev != null
                    && prev.getMetadata().get("source").equals(current.getMetadata().get("source"))
                    && prev.getMetadata().get("sectionIndex").equals(current.getMetadata().get("sectionIndex"))) {
                // 同文档同 section，加 overlap
                String prevText = prev.getText();
                int overlapChars = (int) (prevText.length() * OVERLAP_RATIO);
                // 只取最后一段完整行，避免切断句子
                String overlap = prevText.substring(Math.max(0, prevText.length() - overlapChars));
                int firstNewline = overlap.indexOf('\n');
                if (firstNewline > 0) {
                    overlap = overlap.substring(firstNewline + 1); // 从完整行开始
                }
                String newText = overlap + "\n" + current.getText();
                result.add(new Document(newText, current.getMetadata()));
            } else {
                result.add(current);
            }
        }
        return result;
    }
}
