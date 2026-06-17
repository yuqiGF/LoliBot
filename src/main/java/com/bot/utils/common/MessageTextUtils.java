package com.bot.utils.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ 消息文本工具。
 *
 * <p>Shiro 收到的原始消息里经常混有 CQ 码，例如 @、图片、表情等。插件层只应该关心
 * “用户真正输入了什么”，因此这里统一处理 CQ 码剥离和图片 URL 提取，避免每个插件各写一套正则。</p>
 */
public final class MessageTextUtils {

    private static final Pattern CQ_CODE_PATTERN = Pattern.compile("\\[CQ:[^\\]]+]");
    private static final Pattern CQ_IMAGE_PATTERN = Pattern.compile("\\[CQ:image,([^\\]]+)]");
    private static final Pattern CQ_PARAM_PATTERN = Pattern.compile("(^|,)url=([^,\\]]+)");
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://[^,\\s\\]]+\\.(jpg|jpeg|png|gif|bmp|webp|avif|tiff|svg|ico)",
            Pattern.CASE_INSENSITIVE
    );

    private MessageTextUtils() {
    }

    /**
     * 移除所有 CQ 码并整理空白，返回适合交给 AI 或业务逻辑处理的纯文本。
     */
    public static String plainText(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "";
        }
        return CQ_CODE_PATTERN.matcher(rawMessage)
                .replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 从 CQ 图片码或普通图片链接中提取图片 URL。
     *
     * <p>优先读取 CQ 图片参数里的 url；如果消息不是 CQ 格式，则降级匹配常见图片链接。</p>
     */
    public static String extractImageUrl(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }

        Matcher cqImageMatcher = CQ_IMAGE_PATTERN.matcher(rawMessage);
        while (cqImageMatcher.find()) {
            String params = cqImageMatcher.group(1);
            Matcher urlMatcher = CQ_PARAM_PATTERN.matcher(params);
            if (urlMatcher.find()) {
                return cleanUrl(urlMatcher.group(2));
            }
        }

        Matcher urlMatcher = IMAGE_URL_PATTERN.matcher(rawMessage);
        if (urlMatcher.find()) {
            return cleanUrl(urlMatcher.group());
        }
        return null;
    }

    /**
     * 清理 CQ 码里常见的转义和包裹字符。
     */
    private static String cleanUrl(String url) {
        if (url == null) {
            return null;
        }
        String cleaned = url.trim()
                .replace("\\/", "/")
                .replace("&amp;", "&");
        if ((cleaned.startsWith("'") && cleaned.endsWith("'"))
                || (cleaned.startsWith("\"") && cleaned.endsWith("\""))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }
}
