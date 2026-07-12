package com.learningbuddy.config;

import com.learningbuddy.memory.TokenAwareChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 装配 — 注册官方 ChatMemory(自动注入多轮历史)。
 *
 * <p>记忆链路:ChatMessage 表 → JpaChatMemoryRepository → MessageWindowChatMemory(条数窗口)
 *            → TokenAwareChatMemory(token 裁剪)→ MessageChatMemoryAdvisor(注入到请求)。
 *
 * <p>注意:工具(LearningTools)不在这里注册为 defaultTools。
 * 原因:LearningTools → LlmClient → ChatClient,若 ChatClient 再依赖 LearningTools 会形成循环依赖。
 * 故工具由 Orchestrator 在每次 prompt 时通过 .tools(tools) 显式传入。
 */
@Configuration
public class ChatClientConfig {

    /** 滑动窗口:保留最近 N 条(条数上限,token 上限由 TokenAwareChatMemory 再裁) */
    private static final int WINDOW_MESSAGES = 20;

    @Bean
    public MessageWindowChatMemory messageWindowChatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(WINDOW_MESSAGES)
                .build();
    }

    /** 包装一层 token 预算裁剪 */
    @Bean
    public TokenAwareChatMemory chatMemory(MessageWindowChatMemory windowMemory) {
        return new TokenAwareChatMemory(windowMemory);
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel,
                                 TokenAwareChatMemory chatMemory) {
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .build();
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }
}
