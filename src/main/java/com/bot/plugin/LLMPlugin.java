package com.bot.plugin;

import com.bot.service.DashScopeService;
import com.bot.utils.ai.DeepSeekClient;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.enums.AtEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;

@Component
@Shiro
public class LLMPlugin extends BotPlugin {
    @Resource
    private DashScopeService dashScopeService;

    /**
     * DashScope灵积大模型
     * @param bot
     * @param event
     */
    //群聊消息
    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED) //被at了才会发送
    public void deepSeekTalk(Bot bot, GroupMessageEvent event){
        //用正则表达式把头去掉
        String message = event.getMessage().replaceFirst("\\[CQ:.*?\\]\\s*", "");
        String res = "";
        if (message.isEmpty()){
            //光艾特 不发消息
            res = "Ciallo～(∠・ω< )⌒★";
        }else {
            //让ai认识发消息的人是谁
            message = message + "|" + event.getUserId();
            res = dashScopeService.chat(message);
        }
        String response = MsgUtils.builder()
                .text(res)
//                .at(event.getUserId())
                .build();
        bot.sendGroupMsg(event.getGroupId(),response,false);
    }

    /**
     * 私聊ai
     * @param bot
     * @param event
     */
    @PrivateMessageHandler
    public void aiTalk(Bot bot, PrivateMessageEvent event){
        String msg = event.getMessage();
        String response = dashScopeService.chat(msg);
        String res = MsgUtils.builder()
                .text(response)
                .build();
        bot.sendPrivateMsg(event.getUserId(),res,false);
    }

    /**
     * DeepSeek模型
     * @param bot
     * @param event
     * @param matcher
     * @throws IOException
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^ds\\s(.*)?$")
    public void deepSeekTemp(Bot bot, GroupMessageEvent event, Matcher matcher) throws IOException {
        String name = matcher.group(1);
        DeepSeekClient deepSeekClient = new DeepSeekClient();
        String info = deepSeekClient.chat(event.getMessage());
        String msg = MsgUtils.builder().text(info).build();
        bot.sendGroupMsg(event.getGroupId(),msg,false);
        
    }




}
