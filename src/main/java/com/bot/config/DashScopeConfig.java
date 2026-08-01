package com.bot.config;

import com.bot.guardrail.KeyWordsGuardrail;
import com.bot.service.DashScopeService;
import com.bot.service.LightweightDashScopeService;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 为翻译和结构化整理创建独立的低成本模型。
     *
     * <p>轻量任务不需要聊天记忆、知识库或联网搜索。限制输出长度并降低随机性，
     * 既能减少 Token 消耗，也能让 JSON 结果更加稳定。</p>
     */
    @Bean
    public LightweightDashScopeService lightweightDashScopeService(
            @Value("${langchain4j.community.dashscope.chat-model.api-key}") String apiKey,
            @Value("${bot.ai.lightweight-model-name:qwen-flash}") String modelName
    ) {
        ChatModel lightweightModel = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .enableSearch(false)
                .temperature(0.1f)
                .maxTokens(2048)
                .build();

        return AiServices.builder(LightweightDashScopeService.class)
                .chatModel(lightweightModel)
                .build();
    }
}

