package com.learningbuddy.api;

import com.learningbuddy.rag.DocumentIngestor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * RAG Controller —— 文档上传入口
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final DocumentIngestor ingestor;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, Authentication auth) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty file"));
        }
        Long userId = auth == null ? null :
                (auth.getPrincipal() instanceof com.learningbuddy.security.JwtAuthFilter.AuthenticatedUser u ? u.id() : null);
        var result = ingestor.ingest(file, userId);
        return ResponseEntity.ok(Map.of(
                "docId", result.docId(),
                "filename", result.filename(),
                "chunkCount", result.chunkCount()
        ));
    }
}
