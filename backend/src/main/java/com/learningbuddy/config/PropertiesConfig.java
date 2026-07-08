package com.learningbuddy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自定义配置聚合
 * 统一从 application.yml 的 learningbuddy.* 节点读取
 */
@ConfigurationProperties(prefix = "learningbuddy")
public record PropertiesConfig(
        Jwt jwt,
        Security security,
        Llm llm,
        Zvec zvec,
        Rag rag,
        Cors cors
) {
    public record Jwt(String secret, int ttlHours, String issuer) {}
    public record Security(int bcryptRounds) {}
    public record Llm(boolean fallbackMock, String provider) {}
    public record Zvec(String dataDir, String defaultCollection, int dim, int topK) {}
    public record Rag(int chunkSize, int chunkOverlap) {}
    public record Cors(String allowedOrigins) {}
}
