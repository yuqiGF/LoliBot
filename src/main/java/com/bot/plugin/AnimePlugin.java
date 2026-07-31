package com.bot.plugin;

import com.bot.utils.common.AnimeChineseTranslator;
import com.bot.utils.common.RichCardRenderer;
import com.bot.utils.common.RichCardRenderer.Card;
import com.bot.utils.crawler.AnimeClient;
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
import java.util.regex.Matcher;

/** 最近新番与动漫详情查询。 */
@Component
@Shiro
public class AnimePlugin extends BotPlugin {
    private static final Logger logger = LoggerFactory.getLogger(AnimePlugin.class);
    private final AnimeClient client = new AnimeClient();

    @jakarta.annotation.Resource
    private AnimeChineseTranslator translator;

    @Value("${typst.path:typst}")
    private String typstPath;

    @Value("${typst.font-path:}")
    private String fontPath;

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^(?:anime|番剧)(?:\\s+(.*))?$|^新番$")
    public void query(Bot bot, GroupMessageEvent event, Matcher matcher) {
        String keyword = matcher.groupCount() >= 1 && matcher.group(1) != null ? matcher.group(1).trim() : "";
        try {
            Card card = keyword.isBlank() ? client.latestCard() : client.detailCard(keyword);
            if (card == null) {
                bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder()
                        .text(keyword.isBlank() ? "暂时没有取到新番日程" : "没有查到这部动漫：" + keyword)
                        .build(), false);
                return;
            }
            card = translator.translate(card);
            File image = RichCardRenderer.render(card, typstPath, fontPath,
                    event.getGroupId() + "_" + keyword);
            if (image != null) {
                bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder()
                        .img("file://" + image.getAbsolutePath()).build(), false);
            } else {
                bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder()
                        .text(card.title() + "\n" + card.summary()).build(), false);
            }
        } catch (Exception e) {
            logger.error("动漫查询失败，keyword={}", keyword, e);
            bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text("动漫查询暂时失败，请稍后再试").build(), false);
        }
    }
}