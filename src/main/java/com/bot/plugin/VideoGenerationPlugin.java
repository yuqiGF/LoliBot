package com.bot.plugin;

import com.bot.utils.ai.VideoGenerator;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.AtEnum;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Shiro
public class VideoGenerationPlugin extends BotPlugin {
    /**
     * 视频生成功能 - 当@机器人并包含视频生成指令时触发
     * 在普通聊天处理器之前处理视频生成请求
     */
    private static final Logger logger = LoggerFactory.getLogger(VideoGenerationPlugin.class);

    @Resource
    private VideoGenerator videoGenerator;

    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED) // 视频生成处理器
    public void generateVideoOnAt(Bot bot, GroupMessageEvent event) {
        // 提取消息内容，去掉CQ码
        String message = event.getMessage().replaceFirst("\\[CQ:.*?\\]\\s*", "");

        // 检查是否包含视频生成指令
        if (!message.isEmpty() && isVideoGenerationCommand(message)) {
            try {
                logger.info("收到视频生成请求: {}", message);

                // 提取消息中的图片URL
                String imgUrl = extractImageUrl(event.getMessage());

                if (imgUrl == null) {
                    logger.error("未在消息中找到图片");
                    String errorMsg = MsgUtils.builder()
                            .text("视频生成失败喵~")
                            .text("\n通义万相-图生视频模型需要一张首帧图像和文本描述哦~")
                            .text("\n请在@机器人并发送视频生成指令的同时，附带一张图片作为首帧")
                            .build();
                    bot.sendGroupMsg(event.getGroupId(), errorMsg, false);
                    return;
                }

                logger.info("提取到的图片URL: {}", imgUrl);

                // 先生成一个提示消息告知用户正在处理
                String processingMsg = MsgUtils.builder()
                        .text("正在为你生成视频，请稍等喵~")
                        .text("\n图生视频生成过程可能需要1-5分钟，请耐心等待~")
                        .build();
                bot.sendGroupMsg(event.getGroupId(), processingMsg, false);

                // 调用视频生成API（异步调用，会自动轮询获取结果）
                String videoUrl = videoGenerator.generate(message, imgUrl);

                if (videoUrl != null) {
                    logger.info("视频生成成功: {}", videoUrl);
                    // 发送视频链接，移除使用视频URL作为图片的部分
                    String videoMsg = MsgUtils.builder()
                            .text("视频生成完成啦~")
                            .text("\n视频链接：" + videoUrl)
                            .text("\n提示：视频链接有效期可能有限，请及时保存~")
                            .build();
                    bot.sendGroupMsg(event.getGroupId(), videoMsg, false);
                } else {
                    logger.error("视频生成失败");
                    String errorMsg = MsgUtils.builder()
                            .text("视频生成失败了呢，可能的原因：")
                            .text("\n1. 描述可能不够清晰或包含敏感内容")
                            .text("\n2. API调用限制、网络问题或服务繁忙")
                            .text("\n3. 图片格式或大小不符合要求")
                            .text("\n4. 请检查API密钥是否有效")
                            .build();
                    bot.sendGroupMsg(event.getGroupId(), errorMsg, false);
                }

            } catch (Exception e) {
                logger.error("视频生成过程中发生异常: {}", e.getMessage(), e);
                String errorMsg = MsgUtils.builder()
                        .text("视频生成时出错了喵~")
                        .text("\n错误信息：" + e.getMessage())
                        .text("\n请稍后再试或联系管理员查看日志")
                        .build();
                bot.sendGroupMsg(event.getGroupId(), errorMsg, false);
            }
        }

        // 不包含视频生成指令，框架会自动调用其他匹配的处理器（如deepSeekTalk）
    }

    /**
     * 检查是否为视频生成命令
     */
    private boolean isVideoGenerationCommand(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("生成视频") || lower.contains("视频生成") ||
                lower.contains("做个视频") || lower.contains("video");
    }

    /**
     * 从消息中提取图片URL
     */
    private String extractImageUrl(String message) {
        // 检查消息中是否包含CQ码图片
        if (message.contains("[CQ:image")) {
            try {
                // 查找CQ码图片的URL部分
                int start = message.indexOf("url=") + 4;
                if (start > 3) {
                    // 尝试多种可能的结束标记
                    // 1. 查找逗号（常见于CQ码参数分隔）
                    int end1 = message.indexOf(",", start);
                    // 2. 查找右括号
                    int end2 = message.indexOf(")", start);
                    // 3. 查找右中括号
                    int end3 = message.indexOf("]", start);

                    // 选择最早出现的有效结束标记
                    int end = -1;
                    if (end1 > start) end = end1;
                    if (end2 > start && (end == -1 || end2 < end)) end = end2;
                    if (end3 > start && (end == -1 || end3 < end)) end = end3;

                    if (end > start) {
                        String url = message.substring(start, end);
                        // 处理URL中的转义字符和可能的空格
                        url = url.trim().replace("\\/", "/");
                        // 处理可能的引号包围
                        if (url.startsWith("'") && url.endsWith("'")) {
                            url = url.substring(1, url.length() - 1);
                        } else if (url.startsWith("\"") && url.endsWith("\"")) {
                            url = url.substring(1, url.length() - 1);
                        }
                        return url;
                    }
                }
            } catch (Exception e) {
                logger.error("提取图片URL时发生异常: {}", e.getMessage());
            }
        }

        // 检查是否包含普通的图片URL
        // 增强的URL匹配正则表达式，支持更多图片格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "https?:\\/\\/[^,\\s\\]]+\\.(jpg|jpeg|png|gif|bmp|webp|avif|tiff|svg|ico)",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }
}
