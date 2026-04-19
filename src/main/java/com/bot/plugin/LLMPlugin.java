package com.bot.plugin;

import com.bot.common.GroupNumber;
import com.bot.common.QQNumber;

import com.bot.model.User;
import com.bot.service.DashScopeService;
import com.bot.utils.ai.DeepSeekClient;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.enums.AtEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Shiro
public class LLMPlugin extends BotPlugin {
    @Resource
    private DashScopeService dashScopeService;

    Map<Long, User> userMap = new ConcurrentHashMap<>();  //全局用户列表
    Map<Long, List<String>> messageMap = new ConcurrentHashMap<>();  //全局消息列表
    Random r = new Random();  //随机数

    /**
     * 智能问答   调用ai和指定返回值暂支都写在这里了
     *
     * @param bot
     * @param event
     */
    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED)
    public void qWenTalk(Bot bot, GroupMessageEvent event) {
        //用户不存在的话新建用户
        Long userId = event.getUserId();  //用户id
        isUserExist(event, userId);
        //获取用户信息
        User user = userMap.get(userId);

        //提取消息
        String message = event.getMessage().replaceFirst("\\[CQ:.*?\\]\\s*", "");
        String res;
        if (message.isEmpty()) {
            if (Objects.equals(userId, QQNumber.MASTER)) {
                res = "狗修金 Ciallo～(∠・ω< )⌒★";
            } else {
                int favorability = user.getFavorability(); //好感度
                if (favorability <= 20) {
                    res = user.getNickname() + "是谁，不熟喵~";
                } else if (favorability <= 40) {
                    res = "是" + user.getNickname() + "呀（远远看着）";
                } else if (favorability <= 60) {
                    res = user.getNickname() + "你好呀";
                } else if (favorability <= 80) {
                    res = user.getNickname() + " Ciallo～(∠・ω< )⌒★";
                } else {
                    res = user.getNickname() + "快 过来贴贴喵！";
                }
            }
        } else {
            //⭐特殊用户
//            if (Objects.equals(userId, QQNumber.BadGay)) {
//                res = dashScopeService.chatBadGay(String.valueOf(userId), message);
//            }
            if (Objects.equals(userId, QQNumber.Bird)) {
                res = dashScopeService.chatBird(String.valueOf(userId), message);
            }
            //正常好感度回应
            else {
                String currentMessage = user.getNickname() + ":\n" + "好感度：" + user.getFavorability() + "\n" + message;
                res = dashScopeService.chat(String.valueOf(userId), currentMessage);
                //兜底回复
                if (res == null || res.isBlank()) res = "喵呜？主人刚才说了什么吗？";
                // 正则提取变动分值
                Pattern p = Pattern.compile("\\[Δ好感度:\\s*([+-]?\\d+)\\]");
                Matcher m = p.matcher(res);
                if (m.find()) {
                    int change = Integer.parseInt(m.group(1));  //好感度变动
                    if (change > 0) { //好感度上升
                        int updatedReplyRate = user.getReplyRate() + 1;
                        user.setReplyRate(Math.max(1, Math.min(8, updatedReplyRate)));  //回复概率变动
                    } else if (change < 0) {
                        int updatedReplyRate = user.getReplyRate() - 1;
                        user.setReplyRate(Math.max(1, Math.min(8, updatedReplyRate)));  //回复概率变动
                    }
                    // 只有非主人状态才进行内存数据的更新
                    if (user.getFavorability() < 101) {
                        int updatedFav = user.getFavorability() + change;
                        // 设置好感度在合法区间
                        user.setFavorability(Math.max(0, Math.min(100, updatedFav)));
                    }
                }
            }
        }

        //发送消息
        bot.sendGroupMsg(
                event.getGroupId(),
                MsgUtils.builder().text(res).build(),
                false
        );
    }


    /**
     * ⭐自动概率回复
     */
    @GroupMessageHandler
    @MessageHandlerFilter(groups = {GroupNumber.AISI, GroupNumber.HUANYAN, GroupNumber.QIQI})  //在指定群组监听
    public void autoTalk(Bot bot, GroupMessageEvent event) {
        Long userId = event.getUserId();  //用户id


        //不存在的话新建用户
        isUserExist(event, userId);

        List<String> messageList = messageMap.computeIfAbsent(event.getGroupId(), k -> new CopyOnWriteArrayList<>());  //获取本群消息列表
        User user = userMap.get(userId);  //获取已封装的用户信息


        //加入对话列表
        String message = event.getMessage().replaceFirst("\\[CQ:.*?\\]\\s*", "");
        messageList.add(user.getNickname() + ":" + message);
        if (messageList.size() < 5) {
            return;
        }
        if (messageList.size() >= 30) {
            messageList.removeFirst();
        }

        //概率回复
        int replyRate = userMap.get(userId).getReplyRate();
        int replyOrNot = r.nextInt(100);
        //触发回复
        if (replyOrNot <= replyRate) {
            StringBuilder sb = new StringBuilder();
            for (String s : messageList) {
                sb.append(s).append("\n");
            }
            String currentMessage = sb.toString();

            //自动接话
            String res = dashScopeService.autoChat(currentMessage);

            bot.sendGroupMsg(event.getGroupId(), res, false);
            messageList.clear();  //清空会话
        }
    }

    //⭐数字提取正则
    private static final Pattern FAV_PATTERN = Pattern.compile("(\\d+)");
    /**
     * 工具方法  判断用户是否存在  用户初始化
     *
     * @param event  事件
     * @param userId 用户id
     */
    private void isUserExist(GroupMessageEvent event, Long userId) {
        userMap.computeIfAbsent(userId, id -> {
            //⭐特殊角色
            if (id == QQNumber.Bird || id == QQNumber.MASTER) {
                return new User(id, id == QQNumber.Bird ? "鸟鸟" : "宇崎崎", 101, 4);
            }
            if(id == QQNumber.BadGay){
                return new User(id , "?" , -1 , 2);
            }

            //⭐⭐第一印象
            String message = event.getMessage();
            try {
                //获取初始好感度
                String s = dashScopeService.firstImage(message);

                //正则 提取数字 做一层保险
                Matcher matcher = FAV_PATTERN.matcher(s);
                if (matcher.find()) {
                    String result = matcher.group(1);
                    int favor = Integer.parseInt(result);
                    favor = Math.max(0, Math.min(100, favor)); // 区间修正
                    return new User(id, event.getSender().getNickname(), favor, 4);
                }else {
                    return new User(id , event.getSender().getNickname(), 45, 4);
                }
            } catch (Exception e) {
                return new User(id , event.getSender().getNickname(), 30, 4);
            }
        });
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "好感度")
    public void getFavor(Bot bot, GroupMessageEvent event) {
        //如果用户不存在 则初始化
        Long userId = event.getSender().getUserId();
        isUserExist(event, userId);
        //获取用户
        User user = userMap.get(userId);
        int favor = user.getFavorability();
        //构造消息
        String message = MsgUtils.builder()
                .at(userId)
                .text("您的好感度为:")
                .text(String.valueOf(favor))
                .build();
        bot.sendGroupMsg(event.getGroupId(), message , false);
    }

    /**
     * 私聊回复
     *
     * @param bot
     * @param event
     */
    @PrivateMessageHandler
    public void aiTalk(Bot bot, PrivateMessageEvent event) {
        String userId = String.valueOf(event.getUserId());
        String response = dashScopeService.chat(userId, event.getMessage());
        bot.sendPrivateMsg(
                event.getUserId(),
                MsgUtils.builder().text(response).build(),
                false
        );
    }


    /**
     * DeepSeek模型
     *
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
        bot.sendGroupMsg(event.getGroupId(), msg, false);
    }
}
