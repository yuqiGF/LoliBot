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
        // 当前未启用额外用户限制，保留扩展点并采用框架默认校验。
        return InputGuardrail.super.validate(userMessage);
    }
}
