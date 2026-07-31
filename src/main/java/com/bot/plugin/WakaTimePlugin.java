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

/** WakaTime 今日与七日编程统计。 */
@Component
@Shiro
public class WakaTimePlugin extends BotPlugin {

    @Value("${wakatime.api-key:${wakatime.api_key:}}")
    private String apiKey;

    @Value("${wakatime.timezone:Asia/Shanghai}")
    private String timezone;

    private WakaTimeClient client;

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            client = new WakaTimeClient(apiKey, timezone);
        }
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^waka$|^编程$")
    public void todayStats(Bot bot, GroupMessageEvent event) {
        send(bot, event, client == null ? null : client.getTodayStats());
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^waka\\s*week$|^编程周报$")
    public void weeklyStats(Bot bot, GroupMessageEvent event) {
        send(bot, event, client == null ? null : client.getWeeklyStats());
    }

    private void send(Bot bot, GroupMessageEvent event, String result) {
        String text;
        if (client == null) {
            text = "WakaTime API Key 未配置，请联系管理员设置 wakatime.api-key";
        } else if (result == null || result.isBlank()) {
            text = "WakaTime 暂时没有成功返回数据，请稍后再试";
        } else {
            text = result;
        }
        bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text(text).build(), false);
    }
}