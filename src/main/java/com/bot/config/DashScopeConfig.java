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
 * DashScope AI 服务配置   组装可用的bean
 */
@Configuration
public class DashScopeConfig {
    
    @Resource
    private ChatModel qwenChatModel;

    //文档增强器（用于RAG）
    @Resource
    private ContentRetriever contentRetriever;
    
    @Resource
    private StreamingChatModel qwenStreamingChatModel;

    @Bean   //在这里编写并构建好bean
    public DashScopeService dashScopeService() {
        return AiServices.builder(DashScopeService.class)
                .chatModel(qwenChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(40))  //记忆
                .contentRetriever(contentRetriever)  //RAG
                .streamingChatModel(qwenStreamingChatModel)  //流式模型
                .inputGuardrails(  //护轨
                        new KeyWordsGuardrail()
                )
                .build();
    }
}

