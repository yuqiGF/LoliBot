package com.bot.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

import java.util.List;

public interface DashScopeService {

    // 默认chat
    @SystemMessage(fromResource = "/prompts/default.txt")
    String chat(
            @MemoryId String userId,
            @UserMessage String message
    );


    // 自动接话
    @SystemMessage(fromResource = "/prompts/auto.txt")
    String autoChat(
            @UserMessage String message
    );

    // 特定的人的chat
    @SystemMessage(fromResource = "/prompts/badgay.txt")
    String chatBadGay(
            @MemoryId String memoryId,
            @UserMessage String message
    );

    /**
     * 和鸟鸟对话
     */
    @SystemMessage(fromResource = "/prompts/bird.txt")
    String chatBird(
            @MemoryId String s,
            @UserMessage String message
    );

    /**
     * 第一印象
     */
    @SystemMessage(fromResource = "/prompts/first.txt")
    String firstImage(
            @UserMessage String message
    );

    //结构化输出  使用java的新特性 record构建类
    //报告
    record Report(String name, List<String> suggestionList){}

//    @SystemMessage(fromResource = "/prompts/default.txt")
    Report chatForReport(String message);

//    @SystemMessage(fromResource = "default.txt")
    Report chatWithRAG(String message);

    //流使输出
    @SystemMessage(fromResource = "default.txt")
    Flux<String> chatString(@MemoryId int memoryId,@UserMessage String message);

}
