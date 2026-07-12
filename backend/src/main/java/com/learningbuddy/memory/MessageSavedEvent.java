package com.learningbuddy.memory;

/**
 * 消息落库事件 —— 解耦 JpaChatMemoryRepository 与 SemanticMemoryService。
 *
 * <p>JpaChatMemoryRepository 保存消息后发此事件,
 * SemanticMemoryService 监听并异步算 embedding。
 * <p>这样 Repository 只依赖 Spring 的 ApplicationEventPublisher,不反向依赖 Service 层,
 * 避免了 ChatClient → ChatMemory → Repository → Service → LlmClient → ChatClient 的循环依赖。
 */
public record MessageSavedEvent(Long messageId, String content) {}
