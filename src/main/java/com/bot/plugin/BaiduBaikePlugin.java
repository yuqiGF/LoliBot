package com.bot.plugin;

import com.bot.utils.common.RichCardRenderer;
import com.bot.utils.common.RichCardRenderer.Card;
import com.bot.utils.crawler.BaiduBaikeCrawler;
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

/** “baidu 词条”百科查询。 */
@Component
@Shiro
public class BaiduBaikePlugin extends BotPlugin {
    private static final Logger logger = LoggerFactory.getLogger(BaiduBaikePlugin.class);
    private final BaiduBaikeCrawler crawler = new BaiduBaikeCrawler();

    @Value("${typst.path:typst}")
    private String typstPath;

    @Value("${typst.font-path:}")
    private String fontPath;

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^baidu(?:\\s+(.+))?$")
    public void query(Bot bot, GroupMessageEvent event, Matcher matcher) {
        String keyword = matcher.group(1) == null ? "" : matcher.group(1).trim();
        if (keyword.isBlank()) {
            bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text("用法：baidu 初音未来").build(), false);
            return;
        }
        try {
            Card card = crawler.query(keyword);
            if (card == null) {
                bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text("百度百科没有找到：" + keyword).build(), false);
                return;
            }
            File image = RichCardRenderer.render(card, typstPath, fontPath, event.getGroupId() + "_" + keyword);
            if (image != null) {
                bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().img("file://" + image.getAbsolutePath()).build(), false);
            } else {
                bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text(card.title() + "\n" + card.summary()).build(), false);
            }
        } catch (Exception e) {
            logger.error("百度百科卡片生成失败，keyword={}", keyword, e);
            bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text("百度百科查询暂时失败，请稍后再试").build(), false);
        }
    }
}