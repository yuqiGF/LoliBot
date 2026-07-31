package com.bot.plugin;

import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMPluginTest {

    @Test
    void filtersKfcSpamButKeepsRealDiscussion() {
        assertTrue(LLMPlugin.isAutoChatNoise("KFC KFC KFC"));
        assertTrue(LLMPlugin.isAutoChatNoise("疯狂星期四，V我50"));
        assertFalse(LLMPlugin.isAutoChatNoise("KFC 新品好吃吗？"));

        Deque<String> context = new ConcurrentLinkedDeque<>();
        context.add("甲(1): KFC");
        context.add("乙(2): 疯狂星期四 V我50");
        context.add("丙(3): 今天天气不错");
        context.add("丁(4): 今天天气不错");
        context.add("戊(5): KFC 新品好吃吗？");

        String filtered = LLMPlugin.buildAutoChatContext(context);
        assertFalse(filtered.contains("V我50"));
        assertTrue(filtered.contains("KFC 新品好吃吗"));
        assertTrue(filtered.indexOf("今天天气不错") == filtered.lastIndexOf("今天天气不错"));
    }

    @Test
    void blocksUnsupportedKfcReply() {
        assertFalse(LLMPlugin.shouldSendAutoReply("KFC，V我50！", "甲(1): 今天天气不错"));
        assertTrue(LLMPlugin.shouldSendAutoReply(
                "听起来新品值得试试。", "甲(1): KFC 新品好吃吗？"));
        assertTrue(LLMPlugin.shouldSendAutoReply(
                "那就去试试 KFC 新品吧。", "甲(1): KFC 新品好吃吗？"));
    }
}
