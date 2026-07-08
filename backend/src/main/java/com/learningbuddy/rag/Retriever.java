package com.learningbuddy.rag;

import com.learningbuddy.config.PropertiesConfig;
import com.learningbuddy.core.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 检索器
 *
 * <p>策略:
 * <ol>
 *   <li>优先用 zvec(若初始化成功)</li>
 *   <li>zvec 不可用时降级到内存余弦相似度实现(开发/离线演示用)</li>
 * </ol>
 *
 * <p>输入:用户问题字符串
 * <p>输出:TopK 命中片段(包含 docId / chunkId / snippet / score)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Retriever {

    private final LlmClient llm;
    private final PropertiesConfig properties;
    /** 降级用的内存向量库 */
    private final Map<Long, List<float[]>> memoryVectors = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> memoryTexts = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> memoryDocNames = new ConcurrentHashMap<>();

    public List<Hit> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        // TODO: 调 zvec.Retrieve(query, topK); 当前走内存降级
        return retrieveInMemory(query, topK);
    }

    /** 内部:把命中文档写入(供 DocumentIngestor 调用) */
    public void indexDocument(long docId, String docName, List<String> chunks, List<float[]> embeddings) {
        memoryVectors.put(docId, new ArrayList<>(embeddings));
        memoryTexts.put(docId, new ArrayList<>(chunks));
        memoryDocNames.put(docId, new ArrayList<>(List.of(docName)));
        log.info("indexed doc={} chunks={}", docId, chunks.size());
    }

    /* -------------------- 内存余弦相似度 -------------------- */

    private List<Hit> retrieveInMemory(String query, int topK) {
        float[] q = llm.embed(query);
        List<Hit> all = new ArrayList<>();
        for (Long docId : memoryVectors.keySet()) {
            List<float[]> vecs = memoryVectors.get(docId);
            List<String> texts = memoryTexts.get(docId);
            String docName = memoryDocNames.getOrDefault(docId, List.of("doc-" + docId)).get(0);
            for (int i = 0; i < vecs.size(); i++) {
                double score = cosine(q, vecs.get(i));
                if (score > 0) {
                    all.add(new Hit(docId, (long) i, docName, texts.get(i), score));
                }
            }
        }
        all.sort((a, b) -> Double.compare(b.score(), a.score()));
        return all.subList(0, Math.min(topK, all.size()));
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na  += a[i] * a[i];
            nb  += b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** 命中片段 */
    public record Hit(Long docId, Long chunkId, String docName, String snippet, double score) {}
}
