package com.learningbuddy.rag;

import com.learningbuddy.config.PropertiesConfig;
import com.learningbuddy.core.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档摄取器:把用户上传的 PDF / TXT / MD 切成小段,嵌入后写入 zvec(降级:内存)。
 *
 * <p>切片策略:固定大小(默认 600 字)+ 重叠 80 字,按字符切,简单可解释。
 * <p>未来可换 SentenceSplitter / 段落感知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIngestor {

    private final LlmClient llm;
    private final Retriever retriever;
    private final PropertiesConfig properties;
    private final Tika tika = new Tika();

    /** 摄取结果:文档 ID + 切片数 */
    public IngestResult ingest(MultipartFile file, Long userId) throws Exception {
        String content = extractText(file);
        List<String> chunks = split(content, properties.rag().chunkSize(), properties.rag().chunkOverlap());
        List<float[]> embs = new ArrayList<>(chunks.size());
        for (String c : chunks) {
            embs.add(llm.embed(c));
        }
        long docId = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        retriever.indexDocument(docId, file.getOriginalFilename(), chunks, embs);
        log.info("ingested file={} user={} chunks={}", file.getOriginalFilename(), userId, chunks.size());
        return new IngestResult(docId, file.getOriginalFilename(), chunks.size());
    }

    private String extractText(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream()) {
            return tika.parseToString(is);
        }
    }

    /** 字符级滑动窗口切片 */
    static List<String> split(String text, int size, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        int n = text.length();
        int step = Math.max(1, size - overlap);
        for (int i = 0; i < n; i += step) {
            int end = Math.min(n, i + size);
            out.add(text.substring(i, end));
            if (end == n) break;
        }
        return out;
    }

    public record IngestResult(long docId, String filename, int chunkCount) {}
}
