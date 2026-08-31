package com.nongpi.fulfillment.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档加载器 — 扫描 classpath:document/*.md，按文件名前缀解析 category 元数据
 * <p>命名约定：&lt;category&gt;-&lt;docName&gt;.md，例如 storage-spec-冷链存储规范.md。
 * 解析后 metadata 携带 {source, category, title}，供 VectorStoreDocumentRetriever 做 filterExpression 检索。</p>
 */
@Component
public class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);
    private static final String DOC_PATTERN = "classpath:document/*.md";

    /**
     * 加载 document 目录下全部 markdown 文档，解析 category 元数据
     */
    public List<Document> loadMarkdowns() {
        List<Document> docs = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(DOC_PATTERN);
            for (Resource res : resources) {
                String filename = res.getFilename();
                if (filename == null) continue;
                String category = parseCategory(filename);
                String title = parseTitle(filename);
                String text = new String(res.getContentAsByteArray(), StandardCharsets.UTF_8);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", filename);
                metadata.put("category", category);
                metadata.put("title", title);
                docs.add(new Document(text, metadata));
                log.info("加载知识文档 [{}] category=[{}]", filename, category);
            }
        } catch (IOException e) {
            log.warn("知识文档加载失败：{}", e.getMessage());
        }
        return docs;
    }

    /**
     * 文件名前缀（第一个 - 之前）即 category，如 storage-spec-冷链存储规范.md → storage-spec
     */
    private String parseCategory(String filename) {
        int idx = filename.indexOf('-');
        String cat = idx > 0 ? filename.substring(0, idx) : "general";
        return cat.endsWith(".md") ? "general" : cat;
    }

    /**
     * 去掉前缀和扩展名得到标题
     */
    private String parseTitle(String filename) {
        int idx = filename.indexOf('-');
        String rest = idx > 0 ? filename.substring(idx + 1) : filename;
        if (rest.endsWith(".md")) rest = rest.substring(0, rest.length() - 3);
        return rest;
    }
}
