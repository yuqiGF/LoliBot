package com.bot.plugin;

import com.bot.utils.crawler.MoeGirlCrawler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;

@Component
@Shiro
public class MoeGirlPlugin extends BotPlugin {
    /**
     * 萌娘百科查询
     * 命令：baka <查询内容>
     * 示例：baka 初音未来
     */
    private static final Logger logger = LoggerFactory.getLogger(MoeGirlPlugin.class);

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^baka\\s(.*)?$")
    public void moeGirl(Bot bot, GroupMessageEvent event, Matcher matcher) throws IOException {
        String name = matcher.group(1);

        if (name == null || name.trim().isEmpty()) {
            String tip = MsgUtils.builder()
                    .text("使用方法：baka <查询内容>\n")
                    .text("示例：baka 初音未来")
                    .build();
            bot.sendGroupMsg(event.getGroupId(), tip, false);
            return;
        }

        try {
            String info = MoeGirlCrawler.getInfo(name);

            // 检查是否包含图片链接
            String imageUrl = extractMoeGirlImageUrl(info);

            // 去除文本中的图片URL行
            String textInfo = info.replaceFirst("🖼️ 图片：.*?\n", "");

            // 构建消息（图片和文本在同一个消息框内）
            StringBuilder messageBuilder = new StringBuilder();

            // 如果有图片，将图片CQ码嵌入到文本开头
            if (imageUrl != null && !imageUrl.isEmpty()) {
                messageBuilder.append("[CQ:image,file=").append(imageUrl).append("]\n");
            }

            // 添加文本信息
            messageBuilder.append(textInfo);

            String msg = MsgUtils.builder()
                    .text(messageBuilder.toString())
                    .build();
            bot.sendGroupMsg(event.getGroupId(), msg, false);

        } catch (Exception e) {
            logger.error("萌娘百科查询失败", e);
            String error = MsgUtils.builder()
                    .text("查询失败：" + e.getMessage())
                    .build();
            bot.sendGroupMsg(event.getGroupId(), error, false);
        }
    }

    /**
     * 从萌娘百科结果中提取图片URL
     */
    private String extractMoeGirlImageUrl(String info) {
        if (info == null) return null;

        String[] lines = info.split("\n");
        for (String line : lines) {
            if (line.startsWith("🖼️ 图片：")) {
                return line.replace("🖼️ 图片：", "").trim();
            }
        }
        return null;
    }

}
