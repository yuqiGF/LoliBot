package com.bot.utils.crawler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bot.utils.common.HttpClientPool;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Jisho 查询客户端。Jisho 的词条数据来自 JMdict，本类只负责获取和筛选词典事实，
 * 不在网络层生成翻译、例句或变形。
 */
@Component
public class JapaneseDictionaryClient {
    private static final Logger logger = LoggerFactory.getLogger(JapaneseDictionaryClient.class);

    @Value("${moji.base-url:https://jisho.org/api/v1/search/words}")
    private String baseUrl = "https://jisho.org/api/v1/search/words";

    public DictionaryEntry query(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String normalized = keyword.trim();
        String url = baseUrl + "?keyword=" + URLEncoder.encode(normalized, StandardCharsets.UTF_8);
        String json = request(url);
        return json == null ? null : parse(normalized, json);
    }

    /** 获取指定 JLPT 等级的一页词条，供学习服务在完整分页范围内均匀抽样。 */
    public List<DictionaryEntry> queryJlptPage(String level, int page) {
        if (level == null || !level.matches("N[1-5]") || page < 1) return List.of();
        String tag = "#jlpt-" + level.toLowerCase(Locale.ROOT);
        String url = baseUrl + "?keyword=" + URLEncoder.encode(tag, StandardCharsets.UTF_8)
                + "&page=" + page;
        String json = request(url);
        return json == null ? List.of() : parsePage(json);
    }

    private String request(String url) {
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");
            request.setHeader("User-Agent", "LoliBot/1.0 JapaneseLearningCard");
            try (CloseableHttpResponse response = client.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                if (status < 200 || status >= 300 || response.getEntity() == null) {
                    logger.warn("日语词典请求失败，status={}, url={}", status, url);
                    return null;
                }
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.warn("日语词典查询异常，url={}, message={}", url, e.getMessage());
            return null;
        }
    }

    /** 从 API 响应中优先选择与原词或读音精确匹配、且较常用的词条。 */
    static DictionaryEntry parse(String query, String json) {
        if (json == null || json.isBlank()) return null;
        JSONObject root = JSON.parseObject(json);
        JSONArray data = root.getJSONArray("data");
        if (data == null || data.isEmpty()) return null;

        Candidate best = null;
        for (int i = 0; i < data.size(); i++) {
            JSONObject entry = data.getJSONObject(i);
            Candidate candidate = candidate(query, entry, i);
            if (candidate != null && (best == null || candidate.score() > best.score())) best = candidate;
        }
        return best == null ? null : best.entry();
    }

    /** 保持 Jisho 返回顺序解析整页，页内下标随后用于等概率抽词。 */
    static List<DictionaryEntry> parsePage(String json) {
        if (json == null || json.isBlank()) return List.of();
        JSONObject root = JSON.parseObject(json);
        JSONArray data = root.getJSONArray("data");
        if (data == null || data.isEmpty()) return List.of();
        List<DictionaryEntry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Candidate candidate = candidate("", data.getJSONObject(i), i);
            if (candidate != null) entries.add(candidate.entry());
        }
        return List.copyOf(entries);
    }

    private static Candidate candidate(String query, JSONObject raw, int index) {
        JSONArray japanese = raw.getJSONArray("japanese");
        JSONArray senses = raw.getJSONArray("senses");
        if (japanese == null || japanese.isEmpty() || senses == null || senses.isEmpty()) return null;

        JSONObject selected = japanese.getJSONObject(0);
        int matchScore = 0;
        for (int i = 0; i < japanese.size(); i++) {
            JSONObject variant = japanese.getJSONObject(i);
            String word = value(variant, "word");
            String reading = value(variant, "reading");
            int score = query.equals(word) ? 100 : query.equals(reading) ? 95 : 0;
            if (score > matchScore) {
                matchScore = score;
                selected = variant;
            }
        }

        String reading = value(selected, "reading");
        String word = firstNonBlank(value(selected, "word"), reading, raw.getString("slug"));
        if (word.isBlank()) return null;

        Set<String> definitions = new LinkedHashSet<>();
        Set<String> parts = new LinkedHashSet<>();
        for (int i = 0; i < senses.size() && definitions.size() < 5; i++) {
            JSONObject sense = senses.getJSONObject(i);
            addStrings(definitions, sense.getJSONArray("english_definitions"), 5);
            addStrings(parts, sense.getJSONArray("parts_of_speech"), 5);
        }
        if (definitions.isEmpty()) return null;

        List<String> levels = new ArrayList<>();
        JSONArray jlpt = raw.getJSONArray("jlpt");
        if (jlpt != null) {
            for (Object item : jlpt) {
                String level = String.valueOf(item).toUpperCase(Locale.ROOT).replace("JLPT-", "");
                if (!level.isBlank()) levels.add(level);
            }
        }

        int score = matchScore + (raw.getBooleanValue("is_common") ? 8 : 0) - index;
        DictionaryEntry entry = new DictionaryEntry(
                word,
                reading.isBlank() ? word : reading,
                List.copyOf(definitions),
                List.copyOf(parts),
                levels,
                raw.getBooleanValue("is_common"),
                "https://jisho.org/search/" + URLEncoder.encode(word, StandardCharsets.UTF_8)
        );
        return new Candidate(entry, score);
    }

    private static void addStrings(Set<String> target, JSONArray source, int limit) {
        if (source == null) return;
        for (Object value : source) {
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) target.add(text);
            if (target.size() >= limit) return;
        }
    }

    private static String value(JSONObject object, String key) {
        String value = object == null ? null : object.getString(key);
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    public record DictionaryEntry(
            String word,
            String reading,
            List<String> englishDefinitions,
            List<String> partsOfSpeech,
            List<String> levels,
            boolean common,
            String source
    ) {
    }

    private record Candidate(DictionaryEntry entry, int score) {
    }
}
