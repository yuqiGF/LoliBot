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

import java.io.IOException;
import java.util.regex.Matcher;

@Component
@Shiro
public class CommonMessage extends BotPlugin {
    /**
     * 私聊消息
     * @param bot
     * @param event
     * @param matcher
     */
    @PrivateMessageHandler
    @MessageHandlerFilter(cmd = "你好")
    public void test1(Bot bot , PrivateMessageEvent event, Matcher matcher){
        String msg = MsgUtils.builder().text("我喜欢你").build();
        bot.sendPrivateMsg(event.getUserId(),msg,false);
    }

    /**
     * 群聊消息
     * @param bot
     * @param event
     * @param matcher
     * @throws IOException
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "啾咪")
    public void kiss(Bot bot, GroupMessageEvent event, Matcher matcher) throws IOException {
        String msg = MsgUtils.builder().text("宇崎崎超级可爱").build();
//        CloseableHttpClient httpClient = HttpClients.createDefault();
//        HttpPost httpPost = new HttpPost("/send_poke");
//        BasicNameValuePair userId = new BasicNameValuePair("user_id", event.getUserId().toString());
//        httpPost.setEntity(new UrlEncodedFormEntity((List<? extends NameValuePair>) userId));
//        httpClient.execute(httpPost);
        bot.sendGroupMsg(event.getGroupId(),msg,false);
    }
}
