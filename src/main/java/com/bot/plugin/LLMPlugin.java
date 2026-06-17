package com.bot.plugin;

import com.bot.common.GroupNumber;
import com.bot.common.QQNumber;
import com.bot.model.User;
import com.bot.service.DashScopeService;
import com.bot.utils.ai.DeepSeekClient;
import com.bot.utils.common.BotCommandUtils;
import com.bot.utils.common.MessageTextUtils;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大模型聊天插件。
 *
 * <p>职责划分：
 * 1. 这里负责 QQ 消息入口、用户状态和命令路由；
 * 2. DashScopeService / DeepSeekClient 负责真正的大模型调用；
 * 3. CQ 码处理放在 MessageTextUtils，避免插件里堆正则。</p>
 */
@Component
@Shiro
public class LLMPlugin extends BotPlugin {

    private static final int MASTER_FAVORABILITY = 101;
    private static final int DEFAULT_FAVORABILITY = 45;
    private static final int FALLBACK_FAVORABILITY = 30;
    private static final int DEFAULT_REPLY_RATE = 4;
    private static final int SPECIAL_REPLY_RATE = 2;
    private static final int MIN_REPLY_RATE = 1;
    private static final int MAX_REPLY_RATE = 8;
    private static final int AUTO_CHAT_MIN_CONTEXT_SIZE = 5;
    private static final int AUTO_CHAT_MAX_CONTEXT_SIZE = 30;

    /**
     * 从模型回复中提取好感度增量，例如：[Δ好感度:+3]。
     */
    private static final Pattern FAV_CHANGE_PATTERN = Pattern.compile("\\[Δ好感度:\\s*([+-]?\\d+)]");

    /**
     * 第一印象接口只需要一个 0-100 的数字，这里做兜底提取。
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    @Resource
    private DashScopeService dashScopeService;

    @Resource
    private DeepSeekClient deepSeekClient;

    /**
     * 当前运行期内的用户状态。后续如果要长期保存好感度，可以把这里替换为数据库或文件存储。
     */
    private final Map<Long, User> userMap = new ConcurrentHashMap<>();

    /**
     * 按群缓存最近聊天上下文，用于低概率自动接话。
     */
    private final Map<Long, List<String>> messageMap = new ConcurrentHashMap<>();

    /**
     * @ 机器人时触发的主聊天入口。
     */
    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED)
    public void qWenTalk(Bot bot, GroupMessageEvent event) {
        Long userId = event.getUserId();
        User user = getOrCreateUser(event, userId);
        String message = MessageTextUtils.plainText(event.getMessage());

        // 视频生成插件也监听 @ 消息；这里主动避让，避免同一条命令被两个插件同时回复。
        if (BotCommandUtils.isVideoGenerationCommand(message)) {
            return;
        }

        String response = message.isBlank()
                ? buildEmptyAtReply(userId, user)
                : chatWithFavorability(userId, user, message);

        bot.sendGroupMsg(
                event.getGroupId(),
                MsgUtils.builder().text(response).build(),
                false
        );
    }

    /**
     * 指定群的自动接话：攒够一定上下文后，按用户回复概率触发。
     */
    @GroupMessageHandler
    @MessageHandlerFilter(groups = {GroupNumber.QIQI})
    public void autoTalk(Bot bot, GroupMessageEvent event) {
        Long userId = event.getUserId();
        User user = getOrCreateUser(event, userId);
        String message = MessageTextUtils.plainText(event.getMessage());
        if (message.isBlank() || BotCommandUtils.isVideoGenerationCommand(message)) {
            return;
        }

        List<String> messageList = messageMap.computeIfAbsent(
                event.getGroupId(),
                ignored -> new CopyOnWriteArrayList<>()
        );
        messageList.add(user.getNickname() + ":" + message);

        if (messageList.size() < AUTO_CHAT_MIN_CONTEXT_SIZE) {
            return;
        }
        while (messageList.size() > AUTO_CHAT_MAX_CONTEXT_SIZE) {
            messageList.remove(0);
        }

        int replyRate = clamp(user.getReplyRate(), MIN_REPLY_RATE, MAX_REPLY_RATE);
        int randomValue = ThreadLocalRandom.current().nextInt(100);
        if (randomValue >= replyRate) {
            return;
        }

        String currentMessage = String.join("\n", messageList);
        String response = dashScopeService.autoChat(currentMessage);
        if (response == null || response.isBlank()) {
            response = "喵呜？刚刚好像没想好怎么接话。";
        }

        bot.sendGroupMsg(event.getGroupId(), response, false);
        messageList.clear();
    }

    /**
     * 查询当前用户好感度。
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "好感度")
    public void getFavor(Bot bot, GroupMessageEvent event) {
        Long userId = event.getSender().getUserId();
        User user = getOrCreateUser(event, userId);

        String message = MsgUtils.builder()
                .at(userId)
                .text("您的好感度为:")
                .text(String.valueOf(user.getFavorability()))
                .build();
        bot.sendGroupMsg(event.getGroupId(), message, false);
    }

    /**
     * 私聊时直接走默认 DashScope 对话。
     */
    @PrivateMessageHandler
    public void aiTalk(Bot bot, PrivateMessageEvent event) {
        String userId = String.valueOf(event.getUserId());
        String response = dashScopeService.chat(userId, MessageTextUtils.plainText(event.getMessage()));
        bot.sendPrivateMsg(
                event.getUserId(),
                MsgUtils.builder().text(response).build(),
                false
        );
    }

    /**
     * DeepSeek 临时问答：群内发送 "ds 问题"。
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^ds\\s*(.*)$")
    public void deepSeekTemp(Bot bot, GroupMessageEvent event, Matcher matcher) {
        String question = matcher.group(1) == null ? "" : matcher.group(1).trim();
        String response = deepSeekClient.chat(question);
        bot.sendGroupMsg(
                event.getGroupId(),
                MsgUtils.builder().text(response).build(),
                false
        );
    }

    /**
     * 获取用户状态；不存在时，根据特殊账号或第一印象模型初始化。
     */
    private User getOrCreateUser(GroupMessageEvent event, Long userId) {
        return userMap.computeIfAbsent(userId, id -> createUser(event, id));
    }

    /**
     * 初始化用户。特殊用户直接给固定设定，普通用户用 firstImage 估算第一印象好感度。
     */
    private User createUser(GroupMessageEvent event, Long userId) {
        if (Objects.equals(userId, QQNumber.Bird) || Objects.equals(userId, QQNumber.MASTER)) {
            String nickname = Objects.equals(userId, QQNumber.Bird) ? "鸟鸟" : "宇崎崎";
            return new User(userId, nickname, MASTER_FAVORABILITY, MAX_REPLY_RATE);
        }
        if (Objects.equals(userId, QQNumber.BadGay) || Objects.equals(userId, QQNumber.QiQi)) {
            return new User(userId, event.getSender().getNickname(), -1, SPECIAL_REPLY_RATE);
        }

        String plainMessage = MessageTextUtils.plainText(event.getMessage());
        try {
            String result = dashScopeService.firstImage(plainMessage);
            Matcher matcher = NUMBER_PATTERN.matcher(result == null ? "" : result);
            if (matcher.find()) {
                int favorability = clamp(Integer.parseInt(matcher.group(1)), 0, 100);
                return new User(userId, event.getSender().getNickname(), favorability, DEFAULT_REPLY_RATE);
            }
            return new User(userId, event.getSender().getNickname(), DEFAULT_FAVORABILITY, DEFAULT_REPLY_RATE);
        } catch (Exception e) {
            return new User(userId, event.getSender().getNickname(), FALLBACK_FAVORABILITY, DEFAULT_REPLY_RATE);
        }
    }

    /**
     * 用户只 @ 机器人、不带内容时，根据当前好感度返回固定问候。
     */
    private String buildEmptyAtReply(Long userId, User user) {
        if (Objects.equals(userId, QQNumber.MASTER)) {
            return "狗修金 Ciallo～(∠・ω< )⌒★";
        }

        int favorability = user.getFavorability();
        if (favorability <= 20) {
            return user.getNickname() + "是谁，不熟喵~";
        }
        if (favorability <= 40) {
            return "是" + user.getNickname() + "呀（远远看着）";
        }
        if (favorability <= 60) {
            return user.getNickname() + "你好呀";
        }
        if (favorability <= 80) {
            return user.getNickname() + " Ciallo～(∠・ω< )⌒★";
        }
        if (favorability <= 100) {
            return user.getNickname() + "快 过来贴贴喵！";
        }
        return user.getNickname() + ":emm我不知道";
    }

    /**
     * 与 DashScope 对话，并根据模型返回的 [Δ好感度:x] 更新用户状态。
     */
    private String chatWithFavorability(Long userId, User user, String message) {
        String response;
        if (Objects.equals(userId, QQNumber.Bird)) {
            response = dashScopeService.chatBird(String.valueOf(userId), message);
        } else {
            String currentMessage = user.getNickname()
                    + ":\n好感度：" + user.getFavorability()
                    + "\n" + message;
            response = dashScopeService.chat(String.valueOf(userId), currentMessage);
            response = applyFavorabilityDelta(user, response);
        }

        if (response == null || response.isBlank()) {
            return "喵呜？主人刚才说了什么吗？";
        }
        return response;
    }

    /**
     * 解析并应用好感度变化，同时把控制标记从最终回复里移除。
     */
    private String applyFavorabilityDelta(User user, String response) {
        if (response == null || response.isBlank()) {
            return response;
        }

        Matcher matcher = FAV_CHANGE_PATTERN.matcher(response);
        if (!matcher.find()) {
            return response;
        }

        int change = Integer.parseInt(matcher.group(1));
        updateReplyRate(user, change);
        if (user.getFavorability() < MASTER_FAVORABILITY) {
            user.setFavorability(clamp(user.getFavorability() + change, 0, 100));
        }

        return matcher.replaceAll("").trim();
    }

    /**
     * 好感度上升时略微提高自动回复概率，下降时略微降低，始终限制在安全区间。
     */
    private void updateReplyRate(User user, int favorabilityChange) {
        if (favorabilityChange == 0) {
            return;
        }
        int delta = favorabilityChange > 0 ? 1 : -1;
        user.setReplyRate(clamp(user.getReplyRate() + delta, MIN_REPLY_RATE, MAX_REPLY_RATE));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
