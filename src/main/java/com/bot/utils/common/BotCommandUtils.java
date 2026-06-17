package com.bot.utils.common;

/**
 * 机器人命令意图判断工具。
 *
 * <p>这里放“多个插件都会关心”的轻量判断，避免同一套关键词散落在不同插件里。</p>
 */
public final class BotCommandUtils {

    private BotCommandUtils() {
    }

    /**
     * 判断一段文本是否在请求视频生成。
     */
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
}
