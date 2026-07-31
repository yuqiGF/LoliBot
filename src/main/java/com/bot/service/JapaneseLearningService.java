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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

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

    private static final int JISHO_PAGE_SIZE = 20;
    private static final int RANDOM_ATTEMPTS = 5;
    private static final List<String> JLPT_LEVELS = List.of("N1", "N2", "N3", "N4", "N5");

    /**
     * Jisho 各 JLPT 标签的当前词条总数。随机时先等概率选等级，再在该等级的完整索引范围
     * 中等概率选下标，因此 N1 到 N5 不会因词库大小不同而失衡。
     */
    private static final Map<String, Integer> JLPT_ENTRY_COUNTS = Map.of(
            "N1", 3440,
            "N2", 1802,
            "N3", 1778,
            "N4", 577,
            "N5", 657
    );
    private final JapaneseDictionaryClient dictionaryClient;
    private final DashScopeService dashScopeService;
    private final IntUnaryOperator randomIndex;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public JapaneseLearningService(JapaneseDictionaryClient dictionaryClient, DashScopeService dashScopeService) {
        this(dictionaryClient, dashScopeService, bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    JapaneseLearningService(
            JapaneseDictionaryClient dictionaryClient,
            DashScopeService dashScopeService,
            IntUnaryOperator randomIndex
    ) {
        this.dictionaryClient = dictionaryClient;
        this.dashScopeService = dashScopeService;
        this.randomIndex = randomIndex;
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
        JapaneseWordCard card = cardForEntry(entry);
        cache.put(query, new CacheEntry(card, Instant.now()));
        return card;
    }

    /**
     * N1 到 N5 五个等级等概率；选定等级后，再在该等级全部 Jisho 词条中等概率取一个。
     * 本群近期学过的词会重抽，连续多次碰撞时才允许再次出现，避免为了去重改变等级分布。
     */
    public JapaneseWordCard randomCard(Collection<String> excludedWords) {
        String level = JLPT_LEVELS.get(nextIndex(JLPT_LEVELS.size()));
        int entryCount = JLPT_ENTRY_COUNTS.get(level);
        Collection<String> excluded = excludedWords == null ? List.of() : excludedWords;

        JapaneseWordCard card = randomFromLevel(level, entryCount, excluded);
        return card != null ? card : randomFromLevel(level, entryCount, List.of());
    }

    private JapaneseWordCard randomFromLevel(
            String level,
            int entryCount,
            Collection<String> excludedWords
    ) {
        for (int attempt = 0; attempt < RANDOM_ATTEMPTS; attempt++) {
            int entryIndex = nextIndex(entryCount);
            int page = entryIndex / JISHO_PAGE_SIZE + 1;
            int offset = entryIndex % JISHO_PAGE_SIZE;
            List<DictionaryEntry> entries = dictionaryClient.queryJlptPage(level, page);
            if (offset >= entries.size()) continue;

            DictionaryEntry entry = entries.get(offset);
            if (excludedWords.contains(entry.word())) continue;
            return cardForEntry(withLevel(entry, level));
        }
        return null;
    }

    private JapaneseWordCard cardForEntry(DictionaryEntry entry) {
        String levels = entry.levels() == null ? "" : String.join(",", entry.levels());
        String cacheKey = entry.word() + "|" + levels;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.createdAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.card();
        }
        JapaneseWordCard card = buildCard(entry);
        cache.put(cacheKey, new CacheEntry(card, Instant.now()));
        return card;
    }

    private static DictionaryEntry withLevel(DictionaryEntry entry, String level) {
        if (entry.levels() != null && entry.levels().contains(level)) return entry;
        return new DictionaryEntry(
                entry.word(), entry.reading(), entry.englishDefinitions(), entry.partsOfSpeech(),
                List.of(level), entry.common(), entry.source()
        );
    }

    private int nextIndex(int bound) {
        return Math.floorMod(randomIndex.applyAsInt(bound), bound);
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