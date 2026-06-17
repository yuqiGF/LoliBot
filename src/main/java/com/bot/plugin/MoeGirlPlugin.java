package com.bot.plugin;

import com.bot.utils.crawler.MoeGirlCrawler;
import com.bot.utils.crawler.MoeGirlCrawler.InfoboxData;
import com.bot.utils.crawler.MoeGirlCardRenderer;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;

@Component
@Shiro
public class MoeGirlPlugin extends BotPlugin {

    private static final Logger logger = LoggerFactory.getLogger(MoeGirlPlugin.class);

    @Value("${typst.path:typst}")
    private String typstPath;

    @Value("${typst.font-path:}")
    private String typstFontPath;

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^baka\\s(.*)?$")
    public void moeGirl(Bot bot, GroupMessageEvent event, Matcher matcher) {
        String name = matcher.group(1);

        if (name == null || name.trim().isEmpty()) {
            String tip = MsgUtils.builder()
                    .text("使用方法：baka <查询内容>\n")
                    .text("示例：baka 琪露诺")
                    .build();
            bot.sendGroupMsg(event.getGroupId(), tip, false);
            return;
        }

        try {
            InfoboxData data = MoeGirlCrawler.getInfo(name);
            if (data == null) {
                bot.sendGroupMsg(event.getGroupId(),
                        "未找到【" + name.trim() + "】的相关信息", false);
                return;
            }

            if (!data.isDisambiguation) {
                File card = MoeGirlCardRenderer.render(data, typstPath,
                        typstFontPath, String.valueOf(event.getGroupId()));
                if (card != null) {
                    bot.sendGroupMsg(event.getGroupId(),
                            MsgUtils.builder().img("file://" + card.getAbsolutePath()).build(),
                            false);
                    return;
                }
            }

            // 拼接文字信息
            StringBuilder text = new StringBuilder();

            if (data.isDisambiguation && data.disambiguationOptions != null
                    && !data.disambiguationOptions.isEmpty()) {
                text.append("【").append(data.pageTitle).append("】是消歧义页，请指定更具体的名称：\n");
                int limit = Math.min(10, data.disambiguationOptions.size());
                for (int i = 0; i < limit; i++) {
                    text.append("  · ").append(data.disambiguationOptions.get(i)).append("\n");
                }
            } else {
                if (data.redirectFrom != null && !data.redirectFrom.isEmpty()) {
                    text.append("重定向至：【").append(data.pageTitle).append("】\n\n");
                } else if (!data.pageTitle.equals(name.trim())) {
                    text.append("重定向至：【").append(data.pageTitle).append("】\n\n");
                } else {
                    text.append("【").append(data.pageTitle).append("】\n");
                }

                for (Map.Entry<String, String> e : data.fields.entrySet()) {
                    text.append(e.getKey()).append("：").append(e.getValue()).append("\n");
                }
                text.append("\n来源：").append(data.sourceUrl);
            }

            bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text(text.toString()).build(), false);

        } catch (Exception e) {
            logger.error("萌娘百科查询失败", e);
            bot.sendGroupMsg(event.getGroupId(),
                    "查询失败：" + e.getMessage(), false);
        }
    }
}
