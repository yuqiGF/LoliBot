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

    /** 独立、无会话记忆的日语学习内容整理，避免影响普通聊天人格和上下文。 */
    @SystemMessage("""
            你是一名面向零基础和初学者的严谨日语教师。用户会提供一条来自 JMdict/Jisho 的日语词条 JSON，
            其中包含词形、读音、英文义项、词性和 JLPT 等级。请把它整理成第一次学习这个词时容易理解的内容。

            只输出合法 JSON，结构必须是：
            {"partOfSpeech":"简洁的中文词性","meanings":["中文义项"],
             "examples":[{"japanese":"简单自然的日语例句","chinese":"准确中文翻译"}],
             "note":"一句简短的入门用法说明或记忆提示"}

            规则：
            1. meanings 按原义项顺序给出最多 4 条自然的简体中文释义，第一条必须是最常用核心义，不扩写不存在的含义。
            2. examples 必须正好 2 条，尽量只用 N5/N4 语法和日常词汇，并使用目标词或它的正确变形。
            3. note 要告诉初学者这个词最常见的使用场景、助词搭配或容易混淆之处，不要写“复习”。
            4. 不得编造读音、词性、固定搭配或文化事实；不确定时宁可写得保守。
            5. 不要 Markdown 代码块、标题、解释或 JSON 以外的任何文字。
            """)
    String localizeJapaneseWord(@UserMessage String json);

    /** 把中文学习目标归一成可供词典查询的常用日语辞书形。 */
    @SystemMessage("""
            你是严谨的日语词典编辑。用户输入一个简体中文词语或短语，请给出最常用、最自然且适合初学者学习的日语辞书形。
            动词必须使用辞书形，形容词使用基本形，名词使用词典标题形式；不要输出句子或多个候选。
            只输出合法 JSON：{"word":"日语辞书形"}。不确定时输出 {"word":""}，不要 Markdown 或解释。
            """)
    String resolveJapaneseLemma(@UserMessage String chinesePhrase);


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
