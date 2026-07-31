package com.bot.utils.common;

/** 多个插件共用的机器人指令意图判断。 */
public final class BotCommandUtils {

    private BotCommandUtils() {
    }

    public static boolean isVideoGenerationCommand(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("生成视频")
                || lower.contains("视频生成")
                || lower.contains("做个视频")
                || lower.contains("video");
    }

    /**
     * 判断消息是否属于机器人功能指令。
     *
     * <p>自动聊天必须跳过这些消息，否则功能插件回复后，AI 还会再插一句。</p>
     */
    public static boolean isBotCommand(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim().toLowerCase();
        return isVideoGenerationCommand(normalized)
                || normalized.equals("prts")
                || normalized.startsWith("prts ")
                || normalized.equals("waka")
                || normalized.equals("编程")
                || normalized.equals("编程周报")
                || normalized.equals("waka week")
                || normalized.equals("anime")
                || normalized.startsWith("anime ")
                || normalized.equals("番剧")
                || normalized.startsWith("番剧 ")
                || normalized.equals("新番")
                || normalized.equals("baidu")
                || normalized.startsWith("baidu ")
                || normalized.equals("琪琪手册")
                || normalized.equals("add")
                || normalized.startsWith("add ")
                || normalized.startsWith("wiki ")
                || normalized.startsWith("baka ")
                || normalized.equals("boom")
                || normalized.startsWith("boom ")
                || normalized.startsWith("扫雷")
                || normalized.equals("ds")
                || normalized.startsWith("ds ")
                || normalized.startsWith("loli ")
                || normalized.equals("好感度");
    }
}
