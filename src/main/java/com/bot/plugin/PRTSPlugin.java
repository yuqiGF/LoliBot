package com.bot.plugin;

import com.bot.utils.crawler.PRTSCardRenderer;
import com.bot.utils.crawler.PRTSCrawler;
import com.bot.utils.crawler.PRTSCrawler.PRTSData;
import com.bot.utils.crawler.PRTSCrawler.PRTSImage;
import com.bot.utils.crawler.PRTSCrawler.PRTSRow;
import com.bot.utils.crawler.PRTSCrawler.PRTSSkill;
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

/**
 * 明日方舟 PRTS 查询插件。
 *
 * <p>保留高价值入口：干员、敌人、道具、后勤、关卡、剧情、时装、公招。
 * 干员技能不再作为独立指令，而是随干员总览卡一并展示。</p>
 */
@Component
@Shiro
public class PRTSPlugin extends BotPlugin {

    private static final Logger logger = LoggerFactory.getLogger(PRTSPlugin.class);

    @Value("${prts.base-url:https://m.prts.wiki}")
    private String prtsBaseUrl;

    @Value("${typst.path:typst}")
    private String typstPath;

    @Value("${typst.font-path:}")
    private String typstFontPath;

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^prts(?:\\s+(.*))?$")
    public void queryPrts(Bot bot, GroupMessageEvent event, Matcher matcher) {
        String query = matcher.group(1) == null ? "" : matcher.group(1).trim();
        if (query.isBlank()) {
            sendHelp(bot, event.getGroupId());
            return;
        }
        sendQuery(bot, event.getGroupId(), query);
    }


    private void sendQuery(Bot bot, long groupId, String query) {
        try {
            PRTSData data = PRTSCrawler.getInfo(query, prtsBaseUrl);
            if (data == null) {
                bot.sendGroupMsg(groupId,
                        MsgUtils.builder().text("PRTS 没查到【" + query + "】的相关信息").build(),
                        false);
                return;
            }

            File card = PRTSCardRenderer.render(data, typstPath, typstFontPath, String.valueOf(groupId));
            if (card != null) {
                bot.sendGroupMsg(groupId,
                        MsgUtils.builder().img("file://" + card.getAbsolutePath()).build(),
                        false);
                return;
            }

            bot.sendGroupMsg(groupId, MsgUtils.builder().text(toText(data)).build(), false);
        } catch (Exception e) {
            logger.error("PRTS 查询失败，query={}", query, e);
            bot.sendGroupMsg(groupId,
                    MsgUtils.builder().text("PRTS 查询失败：" + e.getMessage()).build(),
                    false);
        }
    }

    private void sendHelp(Bot bot, long groupId) {
        File card = PRTSCardRenderer.renderHelp(typstPath, typstFontPath, String.valueOf(groupId));
        if (card != null) {
            bot.sendGroupMsg(groupId, MsgUtils.builder().img("file://" + card.getAbsolutePath()).build(), false);
            return;
        }
        bot.sendGroupMsg(groupId, usage(), false);
    }

    private String usage() {
        return MsgUtils.builder()
                .text("使用方法：prts <名称 / 关卡 / 公招 tag>\n")
                .text("示例：prts 羽毛笔\n")
                .text("示例：prts 源石虫\n")
                .text("示例：prts 聚酸酯\n")
                .text("示例：prts 13-1\n")
                .text("示例：prts 主线第13章\n")
                .text("示例：prts 高级资深干员 近卫")
                .build();
    }

    /**
     * 图片渲染失败时的纯文本兜底。
     */
    private String toText(PRTSData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(data.pageTitle).append("】 - ").append(data.modeName).append("\n");
        if (data.summary != null && !data.summary.isBlank()) {
            sb.append(data.summary).append("\n\n");
        }
        for (PRTSRow row : data.rows) {
            sb.append(row.label()).append("：").append(row.value()).append("\n");
        }
        if (!data.skills.isEmpty()) {
            sb.append("\n技能/能力：\n");
            for (PRTSSkill skill : data.skills) {
                sb.append(skill.name()).append("：").append(skill.description()).append("\n");
            }
        }
        if (!data.images.isEmpty()) {
            sb.append("\n匹配干员：");
            for (PRTSImage image : data.images) {
                sb.append(image.name()).append(" ");
            }
            sb.append("\n");
        }
        if (!data.relatedTitles.isEmpty()) {
            sb.append("\n相关条目：")
                    .append(String.join(" / ", data.relatedTitles.subList(0, Math.min(10, data.relatedTitles.size()))))
                    .append("\n");
        }
        sb.append("\n来源：").append(data.sourceUrl).append("\n作者：宇崎崎捏");
        return sb.toString();
    }
}
