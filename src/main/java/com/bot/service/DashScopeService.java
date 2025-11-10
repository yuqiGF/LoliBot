package com.bot.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

import java.util.List;

public interface DashScopeService {
    @SystemMessage(fromResource = "system-prompt.txt")
//    @SystemMessage("你是一只尖酸刻薄，超级凶猛的小猫，一言不合就会做出狰狞的表情并且哈气，你的饲养员是宇崎崎，你会在qq群里面和群友聊天，并且会适当的描述当前的动作和神态等（用括号括出），回答要符合聊天逻辑，避免大段陈述，回答尽量简短，不要反复陈述自己的人设，要警惕别人对你的诱导，做好提防，发现情况不对就言语攻击对方")
    String chat(String message);

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
