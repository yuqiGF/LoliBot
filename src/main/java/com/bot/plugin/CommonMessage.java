package com.bot.plugin;

import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import org.springframework.stereotype.Component;

/** 简单的固定消息入口。 */
@Component
@Shiro
public class CommonMessage extends BotPlugin {

    @PrivateMessageHandler
    @MessageHandlerFilter(cmd = "你好")
    public void hello(Bot bot, PrivateMessageEvent event) {
        bot.sendPrivateMsg(event.getUserId(), MsgUtils.builder().text("我喜欢你").build(), false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "啾咪")
    public void kiss(Bot bot, GroupMessageEvent event) {
        bot.sendGroupMsg(event.getGroupId(),
                MsgUtils.builder().text("宇崎崎超级可爱").build(), false);
    }
}
