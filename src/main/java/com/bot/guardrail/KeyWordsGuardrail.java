package com.bot.guardrail;


import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Set;

/**
 * 敏感词拦截
 */
public class KeyWordsGuardrail implements InputGuardrail {
    //敏感词列表
    private final Set<String> keywords = Set.of("ciallo");
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {

        String userPrompt = userMessage.singleText();
        for (String keyword : keywords) {
            if (userPrompt.contains(keyword)){
                //当前langchain4j版本只能改写
                return InputGuardrailResult.successWith("【检测到敏感_keywords，输入已被拦截】");
            }
        }

        return InputGuardrailResult.success();
    }
}
