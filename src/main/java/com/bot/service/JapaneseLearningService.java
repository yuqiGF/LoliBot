package com.bot.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bot.model.JapaneseWordCard;
import com.bot.model.JapaneseWordCard.Example;
import com.bot.utils.common.JapaneseConjugator;
import com.bot.utils.crawler.JapaneseDictionaryClient;
import com.bot.utils.crawler.JapaneseDictionaryClient.DictionaryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把词典事实、本地变形规则和中文教学整理组合成完整的入门学习卡片。
 *
 * <p>词典与模型职责刻意分开：读音、词性和义项来自 JMdict/Jisho；模型只负责中文化和
 * 初学者例句；可确定的词形变化由本地规则生成。即使模型暂时不可用，也仍能返回包含
 * 可靠词典信息的降级卡片。</p>
 */
@Component
public class JapaneseLearningService {
    private static final Logger logger = LoggerFactory.getLogger(JapaneseLearningService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(12);
    private static final int MAX_QUERY_LENGTH = 40;

    /** 随机学习只选 N5/N4 附近的日常高频词，不把生僻词直接扔给初学者。 */
    private static final List<String> STUDY_WORDS = List.of(
            "食べる", "飲む", "行く", "来る", "見る", "聞く", "話す", "読む", "書く", "買う",
            "起きる", "寝る", "勉強する", "働く", "休む", "会う", "待つ", "持つ", "作る", "使う",
            "始める", "続ける", "忘れる", "覚える", "考える", "選ぶ", "決める", "教える", "助ける", "頑張る",
            "大きい", "小さい", "新しい", "古い", "高い", "安い", "難しい", "優しい", "面白い", "忙しい",
            "静か", "綺麗", "元気", "大切", "便利", "必要", "心配", "上手", "好き", "苦手",
            "今日", "明日", "昨日", "時間", "友達", "学校", "仕事", "電車", "天気", "気持ち",
            "約束", "準備", "理由", "方法", "予定", "料理", "音楽", "旅行", "家族", "言葉"
    );

    private final JapaneseDictionaryClient dictionaryClient;
    private final DashScopeService dashScopeService;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public JapaneseLearningService(JapaneseDictionaryClient dictionaryClient, DashScopeService dashScopeService) {
        this.dictionaryClient = dictionaryClient;
        this.dashScopeService = dashScopeService;
    }

    /** 查询指定词语；过长输入通常不是词条，直接拒绝以免误触和滥用外部接口。 */
    public JapaneseWordCard lookup(String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.isBlank() || query.length() > MAX_QUERY_LENGTH) return null;

        CacheEntry cached = cache.get(query);
        if (cached != null && cached.createdAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.card();
        }

        DictionaryEntry entry = dictionaryClient.query(query);
        if (shouldResolveChinese(query, entry)) {
            String lemma = resolveChineseLemma(query);
            DictionaryEntry translated = lemma.isBlank() ? null : dictionaryClient.query(lemma);
            if (translated != null) entry = translated;
        }
        if (entry == null) return null;
        JapaneseWordCard card = buildCard(entry);
        CacheEntry value = new CacheEntry(card, Instant.now());
        cache.put(query, value);
        cache.put(entry.word(), value);
        return card;
    }

    /** 从精选词库中随机抽词，调用方可传入本群最近学习过的词以避免连续重复。 */
    public JapaneseWordCard randomCard(Collection<String> excludedWords) {
        List<String> candidates = new ArrayList<>(STUDY_WORDS);
        Collections.shuffle(candidates);
        Collection<String> excluded = excludedWords == null ? List.of() : excludedWords;
        for (String candidate : candidates) {
            if (excluded.contains(candidate)) continue;
            JapaneseWordCard card = lookup(candidate);
            if (card != null) return card;
        }
        // 词典短时返回不完整时再允许近期词，尽可能仍给用户一张学习卡。
        for (String candidate : candidates) {
            JapaneseWordCard card = lookup(candidate);
            if (card != null) return card;
        }
        return null;
    }

    /**
     * 纯汉字输入既可能是日语词，也可能是中文短语。精确命中日语写法时直接使用；
     * 未命中时再让模型转换为日语辞书形，避免把“吃饭”之类中文误当成日语查询结果。
     */
    private static boolean shouldResolveChinese(String query, DictionaryEntry entry) {
        if (!query.matches("[\\p{IsHan}\\s，。！？、]+")) return false;
        if (entry == null) return true;
        return !query.equals(entry.word()) && !query.equals(entry.reading());
    }

    private String resolveChineseLemma(String query) {
        try {
            String response = dashScopeService.resolveJapaneseLemma(query);
            if (response == null) return "";
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end <= start) return "";
            return limit(JSON.parseObject(response.substring(start, end + 1)).getString("word"), 30);
        } catch (Exception e) {
            logger.warn("中文短语转换日语辞书形失败，query={}, message={}", query, e.getMessage());
            return "";
        }
    }

    JapaneseWordCard buildCard(DictionaryEntry entry) {
        LocalizedContent localized = localize(entry);
        String level = entry.levels() == null || entry.levels().isEmpty()
                ? "—" : String.join(" / ", entry.levels());
        return new JapaneseWordCard(
                entry.word(), entry.reading(), localized.partOfSpeech(), level, entry.common(),
                localized.meanings(), JapaneseConjugator.conjugate(entry.word(), entry.partsOfSpeech()),
                localized.examples(), localized.note(), entry.source()
        );
    }

    private LocalizedContent localize(DictionaryEntry entry) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("word", entry.word());
            payload.put("reading", entry.reading());
            payload.put("englishDefinitions", entry.englishDefinitions());
            payload.put("partsOfSpeech", entry.partsOfSpeech());
            payload.put("jlpt", entry.levels());
            String response = dashScopeService.localizeJapaneseWord(JSON.toJSONString(payload));
            LocalizedContent parsed = parseLocalized(response);
            if (parsed != null) return parsed;
        } catch (Exception e) {
            logger.warn("日语词条中文整理失败，word={}, message={}", entry.word(), e.getMessage());
        }
        return fallback(entry);
    }

    /** 模型偶尔会包裹代码块，因此只截取最外层 JSON，并严格限制字段数量和长度。 */
    static LocalizedContent parseLocalized(String response) {
        if (response == null || response.isBlank()) return null;
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        JSONObject root = JSON.parseObject(response.substring(start, end + 1));
        String part = limit(root.getString("partOfSpeech"), 40);
        List<String> meanings = strings(root.getJSONArray("meanings"), 4, 80);
        List<Example> examples = new ArrayList<>();
        JSONArray rawExamples = root.getJSONArray("examples");
        if (rawExamples != null) {
            for (int i = 0; i < rawExamples.size() && examples.size() < 2; i++) {
                JSONObject item = rawExamples.getJSONObject(i);
                String japanese = limit(item == null ? null : item.getString("japanese"), 120);
                String chinese = limit(item == null ? null : item.getString("chinese"), 120);
                if (!japanese.isBlank() && !chinese.isBlank()) examples.add(new Example(japanese, chinese));
            }
        }
        String note = limit(root.getString("note"), 120);
        if (part.isBlank() || meanings.isEmpty()) return null;
        return new LocalizedContent(part, meanings, List.copyOf(examples), note);
    }

    private static LocalizedContent fallback(DictionaryEntry entry) {
        List<String> meanings = entry.englishDefinitions().stream()
                .limit(4)
                .map(value -> "英文义项：" + value)
                .toList();
        return new LocalizedContent(
                translatePartOfSpeech(entry.partsOfSpeech()), meanings, List.of(),
                "中文入门讲解暂时不可用，可先依据词典义项认识这个词。"
        );
    }

    private static String translatePartOfSpeech(List<String> parts) {
        if (parts == null || parts.isEmpty()) return "词性未标注";
        String joined = String.join(" ", parts).toLowerCase(Locale.ROOT);
        List<String> translated = new ArrayList<>();
        addIf(translated, joined, "ichidan verb", "一段动词");
        addIf(translated, joined, "godan verb", "五段动词");
        addIf(translated, joined, "transitive", "他动词");
        addIf(translated, joined, "intransitive", "自动词");
        addIf(translated, joined, "suru verb", "サ变动词");
        addIf(translated, joined, "noun", "名词");
        addIf(translated, joined, "i-adjective", "い形容词");
        addIf(translated, joined, "na-adjective", "な形容词");
        addIf(translated, joined, "adverb", "副词");
        addIf(translated, joined, "expression", "固定表达");
        addIf(translated, joined, "particle", "助词");
        return translated.isEmpty() ? String.join(" / ", parts) : String.join(" / ", translated);
    }

    private static void addIf(List<String> values, String source, String token, String value) {
        if (source.contains(token) && !values.contains(value)) values.add(value);
    }

    private static List<String> strings(JSONArray array, int maxItems, int maxLength) {
        if (array == null) return List.of();
        List<String> values = new ArrayList<>();
        for (Object raw : array) {
            String value = limit(raw == null ? null : String.valueOf(raw), maxLength);
            if (!value.isBlank() && !values.contains(value)) values.add(value);
            if (values.size() >= maxItems) break;
        }
        return List.copyOf(values);
    }

    private static String limit(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }

    record LocalizedContent(String partOfSpeech, List<String> meanings, List<Example> examples, String note) {
    }

    private record CacheEntry(JapaneseWordCard card, Instant createdAt) {
    }
}