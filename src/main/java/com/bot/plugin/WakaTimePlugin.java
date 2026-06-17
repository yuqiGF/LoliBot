package com.bot.plugin;

import com.bot.utils.crawler.WakaTimeClient;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;

@Component
@Shiro
public class WakaTimePlugin extends BotPlugin {

    @Value("${wakatime.api_key:}")
    private String apiKey;

    private WakaTimeClient client;

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            this.client = new WakaTimeClient(apiKey);
        }
    }

    /**
     * 今日编程时间
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^waka$|^编程$")
    public void todayStats(Bot bot, GroupMessageEvent event, Matcher matcher) {
        if (client == null) {
            bot.sendGroupMsg(event.getGroupId(),
                    MsgUtils.builder().text("WakaTime API Key 未配置，请联系管理员设置 wakatime.api_key").build(),
                    false);
            return;
        }

        String stats = client.getTodayStats();
        if (stats == null) {
            bot.sendGroupMsg(event.getGroupId(),
                    MsgUtils.builder().text("获取 WakaTime 数据失败，请稍后再试").build(),
                    false);
            return;
        }

        String msg = MsgUtils.builder()
                .text("今日编程时间: " + stats)
                .build();
        bot.sendGroupMsg(event.getGroupId(), msg, false);
    }

    /**
     * 最近7天编程统计
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^waka\\s*week$|^编程周报$")
    public void weeklyStats(Bot bot, GroupMessageEvent event, Matcher matcher) {
        if (client == null) {
            bot.sendGroupMsg(event.getGroupId(),
                    MsgUtils.builder().text("WakaTime API Key 未配置，请联系管理员设置 wakatime.api_key").build(),
                    false);
            return;
        }

        String stats = client.getWeeklyStats();
        if (stats == null) {
            bot.sendGroupMsg(event.getGroupId(),
                    MsgUtils.builder().text("获取 WakaTime 数据失败，请稍后再试").build(),
                    false);
            return;
        }

        bot.sendGroupMsg(event.getGroupId(),
                MsgUtils.builder().text(stats).build(),
                false);
    }
}
