package com.bot.plugin;

import com.bot.model.JapaneseWordCard;
import com.bot.service.JapaneseLearningService;
import com.bot.utils.common.JapaneseWordCardRenderer;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

/** 群聊日语辞书学习卡：指定词查询，或单发 moji 随机学习一个入门词。 */
@Component
@Shiro
public class JapaneseDictionaryPlugin extends BotPlugin {
    private static final Logger logger = LoggerFactory.getLogger(JapaneseDictionaryPlugin.class);
    private static final int RECENT_WORD_LIMIT = 12;
    static final String COMMAND_PATTERN = "(?i)^(?:moji|moji(?:\\s+|\\+)(.+)|moji([一-龯々ぁ-ゖァ-ヺー].*)|([一-龯々ぁ-ゖァ-ヺー]+)\\s*moji)$";

    private final JapaneseLearningService learningService;
    private final Map<Long, Deque<String>> recentWordsByGroup = new ConcurrentHashMap<>();

    @Value("${typst.path:typst}")
    private String typstPath;

    @Value("${typst.font-path:}")
    private String fontPath;

    public JapaneseDictionaryPlugin(JapaneseLearningService learningService) {
        this.learningService = learningService;
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = COMMAND_PATTERN)
    public void query(Bot bot, GroupMessageEvent event, Matcher matcher) {
        String query = extractQuery(matcher);
        long groupId = event.getGroupId();
        try {
            JapaneseWordCard card;
            if (query.isBlank()) {
                card = learningService.randomCard(recentWords(groupId));
            } else if (query.length() > 40) {
                bot.sendGroupMsg(groupId, MsgUtils.builder()
                        .text("这个短语有点长，请输入一个单词或简短中文表达哦").build(), false);
                return;
            } else {
                card = learningService.lookup(query);
            }

            if (card == null) {
                bot.sendGroupMsg(groupId, MsgUtils.builder()
                        .text(query.isBlank() ? "暂时没有抽到学习词，请稍后再试" : "没有找到适合“" + query + "”的日语辞书词条")
                        .build(), false);
                return;
            }

            remember(groupId, card.word());
            File image = JapaneseWordCardRenderer.render(
                    card, typstPath, fontPath, groupId + "_" + card.word());
            if (image != null) {
                bot.sendGroupMsg(groupId, MsgUtils.builder()
                        .img("file://" + image.getAbsolutePath()).build(), false);
            } else {
                bot.sendGroupMsg(groupId, MsgUtils.builder()
                        .text(card.word() + "（" + card.reading() + "）\n" + String.join("；", card.meanings()))
                        .build(), false);
            }
        } catch (Exception e) {
            logger.error("日语学习卡查询失败，groupId={}, query={}", groupId, query, e);
            bot.sendGroupMsg(groupId, MsgUtils.builder()
                    .text("日语学习卡暂时生成失败，请稍后再试").build(), false);
        }
    }

    static String extractQuery(Matcher matcher) {
        if (matcher == null) return "";
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private List<String> recentWords(long groupId) {
        Deque<String> recent = recentWordsByGroup.get(groupId);
        if (recent == null) return List.of();
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }

    private void remember(long groupId, String word) {
        Deque<String> recent = recentWordsByGroup.computeIfAbsent(groupId, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            recent.remove(word);
            recent.addFirst(word);
            while (recent.size() > RECENT_WORD_LIMIT) recent.removeLast();
        }
    }
}