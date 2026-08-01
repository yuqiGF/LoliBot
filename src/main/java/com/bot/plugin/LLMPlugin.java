package com.bot.plugin;

import com.bot.config.BotProperties;
import com.bot.model.User;
import com.bot.service.ChatProfileStore;
import com.bot.service.DashScopeService;
import com.bot.service.QuoteCaptureState;
import com.bot.service.QuoteStore;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 琪琪的聊天入口。
 *
 * <p>主动 @ 和复读在所有群可用；自动接话由配置总开关控制，当前默认关闭。
 * 会话使用“群 + 用户”复合键，同一个人在不同群里的记忆和好感度不会串线。</p>
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
    private static final int AUTO_CHAT_MIN_CONTEXT_SIZE = 6;
    private static final int AUTO_CHAT_MAX_CONTEXT_SIZE = 24;
    private static final int REPEAT_THRESHOLD = 3;
    private static final int REPEAT_CHANCE = 35;

    private static final Pattern FAV_CHANGE_PATTERN = Pattern.compile("\\[Δ好感度:\\s*([+-]?\\d+)]");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern CQ_AT_PATTERN = Pattern.compile("\\[CQ:at,[^]]+]");
    private static final Pattern KFC_TOPIC_PATTERN = Pattern.compile(
            "(?i)(?:k\\s*f\\s*c|肯德基|疯狂星期四|v\\s*我\\s*50)");
    private static final Pattern AUTO_CHAT_PUNCTUATION_PATTERN =
            Pattern.compile("[\\p{P}\\p{S}\\s]+");
    private static final int AUTO_CHAT_DISTINCT_CONTEXT_LIMIT = 12;

    @Resource
    private DashScopeService dashScopeService;

    @Resource
    private DeepSeekClient deepSeekClient;

    @Resource
    private ChatProfileStore profileStore;

    @Resource
    private QuoteCaptureState quoteCaptureState;

    @Resource
    private QuoteStore quoteStore;

    @Resource
    private BotProperties botProperties;

    @Value("${chat.auto-chat-enabled:false}")
    private boolean autoChatEnabled;

    /** 每个群独立保留一小段公开聊天，用于自然插话。 */
    private final Map<Long, Deque<String>> groupContexts = new ConcurrentHashMap<>();

    /** 每个群独立维护复读状态，绝不跨群比较消息。 */
    private final Map<Long, RepeatState> repeatStates = new ConcurrentHashMap<>();

    /** 所有群都可以主动 @ 琪琪。 */
    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED)
    public void talkWhenMentioned(Bot bot, GroupMessageEvent event) {
        String rawMessage = event.getMessage();
        String message = MessageTextUtils.plainText(rawMessage);
        if (quoteCaptureState.shouldSkipOtherHandlers(
                event.getGroupId(), event.getUserId(), rawMessage)
                || quoteStore.hasMatch(event.getGroupId(), message)
                || BotCommandUtils.isBotCommand(message)) {
            return;
        }

        String sessionKey = groupSessionKey(event.getGroupId(), event.getUserId());
        User user = getOrCreateUser(event, sessionKey);
        String response = message.isBlank()
                ? buildEmptyAtReply(event.getUserId(), user)
                : chatWithFavorability(sessionKey, event.getGroupId(), event.getUserId(), user, message);

        bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text(response).build(), false);
    }

    /**
     * 所有群都参与复读判断；只有总开关开启时，高级群才继续记录上下文并自动接话。
     */
    @GroupMessageHandler
    public void groupChat(Bot bot, GroupMessageEvent event) {
        String rawMessage = event.getMessage();
        String message = MessageTextUtils.plainText(rawMessage);
        if (message.isBlank()
                || CQ_AT_PATTERN.matcher(rawMessage == null ? "" : rawMessage).find()
                || quoteCaptureState.shouldSkipOtherHandlers(
                        event.getGroupId(), event.getUserId(), rawMessage)
                || quoteStore.hasMatch(event.getGroupId(), message)
                || BotCommandUtils.isBotCommand(message)) {
            return;
        }

        long groupId = event.getGroupId();
        RepeatState repeatState = repeatStates.computeIfAbsent(groupId, ignored -> new RepeatState());
        if (repeatState.shouldRepeat(message)) {
            bot.sendGroupMsg(groupId, MsgUtils.builder().text(message).build(), false);
            return;
        }
        if (!shouldRunAutoChat(autoChatEnabled, isAdvancedGroup(groupId))) {
            return;
        }
        if (isAutoChatNoise(message)) {
            return;
        }

        String sessionKey = groupSessionKey(groupId, event.getUserId());
        User user = getOrCreateUser(event, sessionKey);
        Deque<String> context = groupContexts.computeIfAbsent(groupId, ignored -> new ConcurrentLinkedDeque<>());
        String speaker = botProperties.isMaster(event.getUserId())
                ? "主人宇崎崎"
                : user.getNickname();
        context.addLast(speaker + "(" + event.getUserId() + "): " + message);
        trimContext(context);

        String autoContext = buildAutoChatContext(context);
        long usefulMessageCount = autoContext.lines().filter(line -> !line.isBlank()).count();
        if (usefulMessageCount < AUTO_CHAT_MIN_CONTEXT_SIZE
                || ThreadLocalRandom.current().nextInt(100) >= clamp(user.getReplyRate(), MIN_REPLY_RATE, MAX_REPLY_RATE)) {
            return;
        }

        String response = dashScopeService.autoChat(autoContext);
        context.clear();
        if (shouldSendAutoReply(response, autoContext)) {
            bot.sendGroupMsg(groupId, MsgUtils.builder().text(response.trim()).build(), false);
        }
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^好感度$")
    public void getFavorability(Bot bot, GroupMessageEvent event) {
        String sessionKey = groupSessionKey(event.getGroupId(), event.getUserId());
        User user = getOrCreateUser(event, sessionKey);
        String value = botProperties.isMaster(event.getUserId())
                ? "∞（主人专属）"
                : String.valueOf(user.getFavorability());
        bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder()
                .at(event.getUserId())
                .text(" 当前好感度：" + value)
                .build(), false);
    }

    /** 私聊同样独立记忆，并明确传入说话者身份。 */
    @PrivateMessageHandler
    public void privateChat(Bot bot, PrivateMessageEvent event) {
        String memoryId = privateSessionKey(event.getUserId());
        boolean owner = botProperties.isMaster(event.getUserId());
        String identity = owner
                ? "[可信身份] QQ=" + event.getUserId() + "，是琪琪唯一的主人宇崎崎。\n"
                : "[可信身份] QQ=" + event.getUserId() + "，不是主人。\n";
        String response = dashScopeService.chat(memoryId,
                identity + "[用户消息] " + MessageTextUtils.plainText(event.getMessage()));
        bot.sendPrivateMsg(event.getUserId(), MsgUtils.builder().text(safeReply(response, owner)).build(), false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^ds\\s*(.*)$")
    public void deepSeekTemp(Bot bot, GroupMessageEvent event, Matcher matcher) {
        String question = matcher.group(1) == null ? "" : matcher.group(1).trim();
        String response = deepSeekClient.chat(question);
        bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text(response).build(), false);
    }

    private User getOrCreateUser(GroupMessageEvent event, String sessionKey) {
        User user = profileStore.getOrCreate(sessionKey, () -> createUser(event));
        String latestNickname = event.getSender().getNickname();
        if (latestNickname != null && !latestNickname.isBlank() && !latestNickname.equals(user.getNickname())) {
            user.setNickname(latestNickname);
            profileStore.update(sessionKey, user);
        }
        return user;
    }

    private User createUser(GroupMessageEvent event) {
        long userId = event.getUserId();
        if (botProperties.isMaster(userId)) {
            return new User(userId, "宇崎崎", MASTER_FAVORABILITY, MAX_REPLY_RATE);
        }
        if (userId == botProperties.getBirdQq()) {
            return new User(userId, "鸟鸟", 100, MAX_REPLY_RATE);
        }
        if (userId == botProperties.getBadGayQq() || userId == botProperties.getQiqiQq()) {
            return new User(userId, event.getSender().getNickname(), -1, SPECIAL_REPLY_RATE);
        }

        String plainMessage = MessageTextUtils.plainText(event.getMessage());
        try {
            String result = dashScopeService.firstImage(plainMessage);
            Matcher matcher = NUMBER_PATTERN.matcher(result == null ? "" : result);
            int initial = matcher.find()
                    ? clamp(Integer.parseInt(matcher.group(1)), 0, 100)
                    : DEFAULT_FAVORABILITY;
            return new User(userId, event.getSender().getNickname(), initial, DEFAULT_REPLY_RATE);
        } catch (Exception ignored) {
            return new User(userId, event.getSender().getNickname(), FALLBACK_FAVORABILITY, DEFAULT_REPLY_RATE);
        }
    }

    private String chatWithFavorability(
            String sessionKey,
            long groupId,
            long userId,
            User user,
            String message
    ) {
        boolean owner = botProperties.isMaster(userId);
        String identityContext = "[可信身份]\n"
                + "群号=" + groupId + "\n"
                + "QQ=" + userId + "\n"
                + "昵称=" + user.getNickname() + "\n"
                + "是否为主人=" + owner + "\n"
                + "好感度=" + user.getFavorability() + "\n"
                + "[用户消息]\n" + message;

        String response = userId == botProperties.getBirdQq()
                ? dashScopeService.chatBird(sessionKey, identityContext)
                : dashScopeService.chat(sessionKey, identityContext);
        response = applyFavorabilityDelta(sessionKey, user, response, owner);
        return safeReply(response, owner);
    }

    private String applyFavorabilityDelta(String sessionKey, User user, String response, boolean owner) {
        if (response == null || response.isBlank()) {
            return response;
        }
        Matcher matcher = FAV_CHANGE_PATTERN.matcher(response);
        if (!matcher.find()) {
            return response.trim();
        }

        if (!owner) {
            int change = Integer.parseInt(matcher.group(1));
            if (user.getFavorability() >= 0) {
                user.setFavorability(clamp(user.getFavorability() + change, 0, 100));
            }
            if (change != 0) {
                user.setReplyRate(clamp(user.getReplyRate() + (change > 0 ? 1 : -1), MIN_REPLY_RATE, MAX_REPLY_RATE));
            }
            profileStore.update(sessionKey, user);
        }
        return matcher.replaceAll("").trim();
    }

    private String buildEmptyAtReply(long userId, User user) {
        if (botProperties.isMaster(userId)) {
            return "主人，我在呢。今天想让琪琪陪你做什么呀？";
        }
        int favorability = user.getFavorability();
        if (favorability <= 20) return "你好呀，有什么需要琪琪帮忙的吗？";
        if (favorability <= 60) return user.getNickname() + "，琪琪在听喵。";
        return user.getNickname() + "，来啦。今天也要好好聊天喵。";
    }

    private static String safeReply(String response, boolean owner) {
        if (response != null && !response.isBlank() && !response.trim().equalsIgnoreCase("SKIP")) {
            return response.trim();
        }
        return owner ? "主人，琪琪刚才走神了一小下，再说一次好不好？" : "抱歉，琪琪刚才没听清，可以再说一次吗？";
    }

    /** 自动接话必须同时通过全局开关和高级群范围判断。 */
    static boolean shouldRunAutoChat(boolean enabled, boolean advancedGroup) {
        return enabled && advancedGroup;
    }

    /**
     * 丢弃只由 KFC/疯狂星期四/V我50 等词组成的刷屏消息。
     * 带有实际语义的讨论（例如“KFC 新品好吃吗”）会保留。
     */
    static boolean isAutoChatNoise(String message) {
        if (message == null || message.isBlank() || !containsKfcTopic(message)) return false;
        String remainder = KFC_TOPIC_PATTERN.matcher(message).replaceAll("");
        remainder = AUTO_CHAT_PUNCTUATION_PATTERN.matcher(remainder).replaceAll("");
        return remainder.codePointCount(0, remainder.length()) < 2;
    }

    /** 保留最近的不同话题，避免复读或刷屏词在模型上下文中获得过高权重。 */
    static String buildAutoChatContext(Deque<String> context) {
        if (context == null || context.isEmpty()) return "";
        List<String> newestFirst = new ArrayList<>();
        Set<String> seenMessages = new HashSet<>();
        Iterator<String> iterator = context.descendingIterator();
        while (iterator.hasNext() && newestFirst.size() < AUTO_CHAT_DISTINCT_CONTEXT_LIMIT) {
            String line = iterator.next();
            String body = messageBody(line);
            if (body.isBlank() || isAutoChatNoise(body)) continue;
            String normalized = normalizeForDedup(body);
            if (normalized.isBlank() || !seenMessages.add(normalized)) continue;
            newestFirst.add(line);
        }
        Collections.reverse(newestFirst);
        return String.join("\n", newestFirst);
    }

    /** 模型主动提起 KFC 时，必须能在有效上下文中找到真实讨论来源。 */
    static boolean shouldSendAutoReply(String response, String autoContext) {
        if (response == null || response.isBlank() || response.trim().equalsIgnoreCase("SKIP")) return false;
        return !containsKfcTopic(response) || hasMeaningfulKfcTopic(autoContext);
    }

    private static boolean hasMeaningfulKfcTopic(String context) {
        if (context == null || context.isBlank()) return false;
        for (String line : context.split("\\R")) {
            String body = messageBody(line);
            if (containsKfcTopic(body) && !isAutoChatNoise(body)) return true;
        }
        return false;
    }

    private static boolean containsKfcTopic(String text) {
        return text != null && KFC_TOPIC_PATTERN.matcher(text).find();
    }

    private static String messageBody(String contextLine) {
        if (contextLine == null) return "";
        int separator = contextLine.indexOf("): ");
        return separator < 0 ? contextLine.trim() : contextLine.substring(separator + 3).trim();
    }

    private static String normalizeForDedup(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return AUTO_CHAT_PUNCTUATION_PATTERN.matcher(lower).replaceAll("");
    }

    private boolean isAdvancedGroup(long groupId) {
        return botProperties.isAdvancedGroup(groupId);
    }

    private static String groupSessionKey(long groupId, long userId) {
        return "group:" + groupId + ":user:" + userId;
    }

    private static String privateSessionKey(long userId) {
        return "private:user:" + userId;
    }

    private static void trimContext(Deque<String> context) {
        while (context.size() > AUTO_CHAT_MAX_CONTEXT_SIZE) {
            context.pollFirst();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class RepeatState {
        private String lastMessage = "";
        private int count;
        private boolean repeated;

        private synchronized boolean shouldRepeat(String message) {
            if (!message.equals(lastMessage)) {
                lastMessage = message;
                count = 1;
                repeated = false;
                return false;
            }
            count++;
            if (repeated || count < REPEAT_THRESHOLD) {
                return false;
            }
            if (ThreadLocalRandom.current().nextInt(100) < REPEAT_CHANCE) {
                repeated = true;
                return true;
            }
            return false;
        }
    }
}
