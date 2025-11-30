package com.bot.config;

import com.bot.guardrail.KeyWordsGuardrail;
import com.bot.service.DashScopeService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DashScope AI 服务配置
 */
@Configuration
public class DashScopeConfig {
    
    @Resource
    private ChatModel qwenChatModel;
    
    @Resource
    private ContentRetriever contentRetriever;
    
    @Resource
    private StreamingChatModel qwenStreamingChatModel;

    @Bean
    public DashScopeService dashScopeService() {
        return AiServices.builder(DashScopeService.class)
                .chatModel(qwenChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                .contentRetriever(contentRetriever)
                .streamingChatModel(qwenStreamingChatModel)
                .inputGuardrails(
                        new KeyWordsGuardrail()
                )
                .build();
    }
}

