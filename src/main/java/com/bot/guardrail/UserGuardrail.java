package com.bot.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * 类似spring ai 的advisor
 * 用户拦截
 */
public class UserGuardrail implements InputGuardrail {

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        //获取到用户的id
        String[] userIds = userMessage.toString().split("\\|");
        String userId = null;
        if (userIds.length == 2){
            userId = userIds[1];
        }
        if (userId != null && userId.equals("2328441709")){
            return InputGuardrailResult.success();
        }
        return InputGuardrail.super.validate(userMessage);
    }
}
