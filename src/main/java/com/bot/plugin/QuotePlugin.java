package com.bot.plugin;

import com.bot.service.QuoteCaptureState;
import com.bot.service.QuoteCaptureState.Pending;
import com.bot.service.QuoteStore;
import com.bot.service.QuoteStore.QuoteMatch;
import com.bot.utils.common.BotCommandUtils;
import com.bot.utils.common.MessageTextUtils;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 群语录添加与关键词触发。
 *
 * <p>添加流程分为两步，待添加状态按“群 + 用户”隔离，因此支持同群多人并发。
 * 语录库按群隔离，关键词命中时优先采用最长关键词并从其内容中随机返回一条。</p>
 */
@Component
@Shiro
public class QuotePlugin extends BotPlugin {
    private static final Pattern ADD_PATTERN =
            Pattern.compile("(?i)^add(?:\\s+(.+))?$");

    @Resource
    private QuoteStore quoteStore;

    @Resource
    private QuoteCaptureState captureState;

    @GroupMessageHandler
    public void quote(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();
        long userId = event.getUserId();
        String rawMessage = event.getMessage() == null ? "" : event.getMessage();
        String plainText = MessageTextUtils.plainText(rawMessage);

        Pending pending = captureState.current(groupId, userId);
        if (pending != null) {
            if ("取消".equals(plainText)) {
                captureState.cancel(groupId, userId);
                sendText(bot, groupId, "已取消本次语录添加。");
                return;
            }
            try {
                int count = quoteStore.add(groupId, pending.keyword(), rawMessage, userId);
                captureState.complete(groupId, userId, rawMessage);
                sendText(bot, groupId,
                        "添加成功。关键词“" + pending.keyword() + "”目前有 " + count + " 条语录。");
            } catch (IllegalArgumentException e) {
                sendText(bot, groupId, e.getMessage() + "，请重新发送内容或发送“取消”。");
            }
            return;
        }

        Matcher add = ADD_PATTERN.matcher(plainText);
        if (add.matches()) {
            String keyword = add.group(1) == null ? "" : add.group(1).trim();
            if (keyword.isBlank() || keyword.length() > 30) {
                sendText(bot, groupId, "用法：add 啾咪（关键词长度 1 到 30 个字符）");
                return;
            }
            captureState.begin(groupId, userId, keyword);
            sendText(bot, groupId,
                    "请发送要加入的内容。支持文字、图片和表情包，2 分钟内有效；发送“取消”可退出。");
            return;
        }

        if (plainText.isBlank() || BotCommandUtils.isBotCommand(plainText)) return;
        QuoteMatch match = quoteStore.randomMatch(groupId, plainText);
        if (match != null) {
            bot.sendGroupMsg(groupId, match.message(), false);
        }
    }

    private static void sendText(Bot bot, long groupId, String text) {
        bot.sendGroupMsg(groupId, MsgUtils.builder().text(text).build(), false);
    }
}