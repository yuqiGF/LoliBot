package com.bot.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;


/**
 * 发送指定的消息
 */
@Component
@Shiro
public class CommonMessage extends BotPlugin {

    /**
     * 私聊消息 （测试用）
     */
    @PrivateMessageHandler
    @MessageHandlerFilter(cmd = "你好")
    public void test1(Bot bot , PrivateMessageEvent event, Matcher matcher){
        String msg = MsgUtils.builder().text("我喜欢你").build();
        bot.sendPrivateMsg(event.getUserId(),msg,false);
    }

    /**
     * 宇崎崎超级可爱！
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "啾咪")
    public void kiss(Bot bot, GroupMessageEvent event, Matcher matcher) throws IOException {
        String msg = MsgUtils.builder().text("宇崎崎超级可爱").build();
        bot.sendGroupMsg(event.getGroupId(),msg,false);
    }

    /**
     * 检测到聊天中有“萝莉”立刻告知宇崎崎
     */
    public void loli(Bot bot, GroupMessageEvent event, Matcher matcher) throws IOException {
        String message = event.getMessage();
        if (message.contains("萝莉") || message.contains("yqq") || message.contains("loli")){
            String msg = MsgUtils.builder()
                    .at(2328441709L)
                    .text("这里有Loli！")
                    .build();
            bot.sendGroupMsg(event.getGroupId(),msg,false);
        }
    }

    /**
     * 检测到聊天中有“小说”，立刻通知蛋挞
     */
    public void fiction(Bot bot, GroupMessageEvent event, Matcher matcher) throws IOException {
        String message = event.getMessage();
        if (message.contains("小说")){
            String msg = MsgUtils.builder()
                    .at(1912600950L)
                    .text("蛋挞快去写小说！")
                    .build();
            bot.sendGroupMsg(event.getGroupId(),msg,false);
        }
    }

}
