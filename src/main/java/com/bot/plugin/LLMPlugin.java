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

    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED)
    public void qWenTalk(Bot bot, GroupMessageEvent event){

        String message = event.getMessage().replaceFirst("\\[CQ:.*?\\]\\s*", "");
        String res;

        if (message.isEmpty()){
            res = "Ciallo～(∠・ω< )⌒★";
        } else {

            //角色选择
            Long userId = event.getUserId();
            String role = "default";

            if (userId.equals(2328441709L)) {
                role = "yuqiqi";
            } else if (userId.equals(3439831958L)) {
                role = "xiaoyuan";
            }

            //  memoryId 组合策略
            String memoryId = "qq:group:" + event.getGroupId() + ":" + userId + ":" + role;

            switch (role){
                case "yuqiqi":
                    res = dashScopeService.chatYuqiqi(memoryId, message); break;
                case "xiaoyuan":
                    res = dashScopeService.chatXiaoYuan(memoryId, message); break;
                default:
                    res = dashScopeService.chat(memoryId, message);
            }
        }

        bot.sendGroupMsg(
                event.getGroupId(),
                MsgUtils.builder().text(res).build(),
                false
        );
    }

    @PrivateMessageHandler
    public void aiTalk(Bot bot, PrivateMessageEvent event){

        String userId = String.valueOf(event.getUserId());
        String memoryId = "qq:private:" + userId + ":yuqiqi";

        String response = dashScopeService.chatYuqiqi(memoryId, event.getMessage());

        bot.sendPrivateMsg(
                event.getUserId(),
                MsgUtils.builder().text(response).build(),
                false
        );
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
