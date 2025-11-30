package com.bot.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

import java.util.List;

public interface DashScopeService {
    //和注入chat
    @SystemMessage(fromResource = "system-yuqiqi-prompt.txt")
    String chatYuqiqi(String message);

    //默认chat
    @SystemMessage(fromResource = "system-prompt.txt")
    String chat(String message);

    //小圆是注入的chat
    @SystemMessage(fromResource = "system-xiaoyuan-prompt.txt")
    String chatXiaoYuan(String message);

    @SystemMessage(fromResource = "system-prompt.txt")
    Report chatForReport(String message);

    //结构化输出  使用java的新特性 record构建类
    //报告
    record Report(String name, List<String> suggestionList){}

//    @SystemMessage(fromResource = "system-prompt.txt")
    Report chatWithRAG(String message);

    //流使输出
    @SystemMessage(fromResource = "system-prompt.txt")
    Flux<String> chatString(@MemoryId int memoryId,@UserMessage String message);

}
