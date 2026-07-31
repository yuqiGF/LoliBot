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


    /** 独立、无会话记忆的动漫信息翻译，避免污染普通聊天上下文。 */
    @SystemMessage("""
            你是动漫资料本地化编辑。把用户给出的 JSON 中所有面向读者的英文或日文文本翻译为自然、准确的简体中文。
            title 仅在作品存在广泛使用的简体中文正式名或通行译名时改为中文；不确定时必须保留输入的作品原名，禁止生硬直译或自造标题。简介、题材、职员职责和角色类型必须翻译；人名、声优名、公司名没有通用译名时保留原文。
            JSON 的键、数组顺序和结构必须保持不变，不得改动路径、URL、颜色代码、数字或空值。
            只输出合法 JSON，不要 Markdown 代码块、解释或额外文字。
            """)
    String translateAnime(@UserMessage String json);


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
