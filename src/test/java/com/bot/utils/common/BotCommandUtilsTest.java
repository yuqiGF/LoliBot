package com.bot.utils.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotCommandUtilsTest {

    @Test
    void recognizesAllPublicCommandFamilies() {
        List<String> commands = List.of(
                "prts 羽毛笔", "prts 高级资深干员 近卫",
                "waka", "waka week", "编程", "编程周报",
                "anime", "anime 葬送的芙莉莲", "番剧 芙莉莲", "新番",
                "baidu", "baidu 初音未来",
                "moji", "moji 食べる", "食べるmoji", "moji+吃饭", "moji吃饭",
                "琪琪手册", "add", "add 啾咪", "baka 琪露诺", "boom", "ds", "ds 快速排序", "好感度"
        );
        commands.forEach(command -> assertTrue(BotCommandUtils.isBotCommand(command), command));
    }

    @Test
    void leavesOrdinaryConversationToChatLogic() {
        assertFalse(BotCommandUtils.isBotCommand("琪琪今天开心吗"));
        assertFalse(BotCommandUtils.isBotCommand("最近在学 Java"));
        assertFalse(BotCommandUtils.isBotCommand("arc Tempestissimo"));
        assertFalse(BotCommandUtils.isBotCommand("emoji"));
        assertFalse(BotCommandUtils.isBotCommand("mojito 很好喝"));
    }
}
