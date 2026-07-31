package com.bot.utils.crawler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bot.utils.common.HttpClientPool;
import com.bot.utils.common.RichCardRenderer.Card;
import com.bot.utils.common.RichCardRenderer.CardItem;
import com.bot.utils.common.RichCardRenderer.Field;
import com.bot.utils.common.RichCardRenderer.Section;
import com.bot.utils.common.WebImageUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** AniList 官方 GraphQL 动漫数据客户端。 */
public class AnimeClient {
    private static final Logger logger = LoggerFactory.getLogger(AnimeClient.class);
    private static final String API = "https://graphql.anilist.co";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final Pattern JAPANESE_ALIAS =
            Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}ー・]{2,}");
    private static final Pattern LATIN_ALIAS =
            Pattern.compile("[A-Za-z][A-Za-z0-9:''’!.?&+ -]{3,}");

    private static final String MEDIA_FIELDS = """
            id title { romaji english native } coverImage { extraLarge large color }
            description(asHtml: false) format status source episodes duration season seasonYear
            genres averageScore popularity siteUrl isAdult
            nextAiringEpisode { airingAt episode timeUntilAiring }
            studios(isMain: true) { nodes { name } }
            staff(perPage: 10, sort: RELEVANCE) { edges { role node { name { full native } } } }
            characters(perPage: 10, sort: ROLE) { edges { role node { name { full native } } voiceActors(language: JAPANESE, sort: RELEVANCE) { name { full native } } } }
            """;

    public Card latestCard() {
        long from = Instant.now().getEpochSecond();
        long to = from + 7 * 24 * 3600;
        String query = "query($from:Int,$to:Int){Page(page:1,perPage:16){airingSchedules("
                + "airingAt_greater:$from,airingAt_lesser:$to,sort:TIME){airingAt episode media{"
                + MEDIA_FIELDS + "}}}}";
        JSONObject data = post(query, new JSONObject().fluentPut("from", from).fluentPut("to", to));
        if (data == null) return null;
        JSONArray schedules = object(data, "Page").getJSONArray("airingSchedules");
        if (schedules == null) return null;

        List<CardItem> items = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (int i = 0; i < schedules.size() && items.size() < 9; i++) {
            JSONObject schedule = schedules.getJSONObject(i);
            JSONObject media = schedule.getJSONObject("media");
            if (media == null || media.getBooleanValue("isAdult")) continue;
            int id = media.getIntValue("id");
            if (!seen.add(id)) continue;
            String title = title(media);
            String nativeTitle = object(media, "title").getString("native");
            int episode = schedule.getIntValue("episode");
            long airingAt = schedule.getLongValue("airingAt");
            String meta = "第 " + episode + " 集 · " + formatTime(airingAt);
            String description = joinNonBlank(" / ", nativeTitle, studios(media), join(media.getJSONArray("genres"), 3));
            String image = downloadCover(media, "anime_latest_" + id);
            items.add(new CardItem(title, meta, description, image));
        }
        if (items.isEmpty()) return null;
        return new Card(
                "最近更新的新番", "未来 7 天 · Asia/Shanghai",
                "按 AniList 的全球首播时间排序；具体字幕上线时间可能因平台而异。",
                null, "00A6A6", List.of(), List.of(), "更新日程", items,
                "AniList GraphQL · 查询于 " + java.time.ZonedDateTime.now(ZONE).format(DATE_TIME)
        );
    }

    public Card detailCard(String keyword) {
        JSONObject media = findMedia(keyword);
        if (media == null) return null;

        int id = media.getIntValue("id");
        String title = title(media);
        JSONObject titles = object(media, "title");
        JSONObject next = media.getJSONObject("nextAiringEpisode");
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("原名", firstNonBlank(titles.getString("native"), titles.getString("romaji"))));
        fields.add(new Field("状态", translate(media.getString("status"))));
        fields.add(new Field("类型", translate(media.getString("format"))));
        fields.add(new Field("季度", season(media)));
        fields.add(new Field("集数", media.getInteger("episodes") == null ? "未定" : media.getInteger("episodes") + " 集"));
        fields.add(new Field("单集时长", media.getInteger("duration") == null ? "未知" : media.getInteger("duration") + " 分钟"));
        fields.add(new Field("原作类型", translate(media.getString("source"))));
        fields.add(new Field("制作公司", firstNonBlank(studios(media), "未收录")));
        fields.add(new Field("评分", media.getInteger("averageScore") == null ? "暂无" : media.getInteger("averageScore") + " / 100"));
        if (next != null) {
            fields.add(new Field("下次更新", "第 " + next.getIntValue("episode") + " 集 · "
                    + formatTime(next.getLongValue("airingAt"))));
        }

        String summary = cleanDescription(media.getString("description"));
        List<Section> sections = new ArrayList<>();
        String genres = join(media.getJSONArray("genres"), 8);
        if (!genres.isBlank()) sections.add(new Section("题材", genres));
        String staff = staff(media);
        if (!staff.isBlank()) sections.add(new Section("主要职员 / 作者信息", staff));
        String cast = cast(media);
        if (!cast.isBlank()) sections.add(new Section("主要角色 / 声优", cast));

        return new Card(title, "ANIME · AniList #" + id, summary,
                downloadCover(media, "anime_detail_" + id), "E4576B",
                fields, sections, "", List.of(), media.getString("siteUrl"));
    }

    /**
     * AniList 对中文译名的模糊搜索并不稳定。首次查询失败后，从百度百科提取
     * 日文名或英文名再重试，让群聊中最常见的中文输入也能正常工作。
     */
    private JSONObject findMedia(String keyword) {
        String query = "query($search:String){Media(search:$search,type:ANIME,isAdult:false){"
                + MEDIA_FIELDS + "}}";
        JSONObject data = post(query, new JSONObject().fluentPut("search", keyword));
        JSONObject media = data == null ? null : data.getJSONObject("Media");
        if (media != null || keyword == null || keyword.codePoints().noneMatch(code -> code > 0x7F)) return media;

        Card baike = new BaiduBaikeCrawler().query(keyword);
        if (baike == null) return null;
        Set<String> aliases = new LinkedHashSet<>();
        for (Field field : baike.fields()) {
            if (field.name().matches(".*(外文名|日文名|英文名|原名|别名).*")) {
                addAliasCandidates(aliases, field.value());
            }
        }
        for (String alias : aliases) {
            data = post(query, new JSONObject().fluentPut("search", alias));
            media = data == null ? null : data.getJSONObject("Media");
            if (media != null) return media;
        }
        return null;
    }

    private static void addAliasCandidates(Set<String> aliases, String value) {
        if (value == null || value.isBlank()) return;
        Matcher japanese = JAPANESE_ALIAS.matcher(value);
        while (japanese.find()) aliases.add(japanese.group().trim());

        Matcher latin = LATIN_ALIAS.matcher(value);
        while (latin.find()) {
            String candidate = latin.group().replaceAll("[?？. ]+$", "").trim();
            if (candidate.length() >= 4) aliases.add(candidate);
        }
    }
    private JSONObject post(String query, JSONObject variables) {
        JSONObject request = new JSONObject().fluentPut("query", query).fluentPut("variables", variables);
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpPost post = new HttpPost(API);
            post.setHeader("Content-Type", "application/json; charset=UTF-8");
            post.setHeader("Accept", "application/json");
            post.setEntity(new StringEntity(request.toJSONString(), StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = client.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                String body = response.getEntity() == null ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (status < 200 || status >= 300 || body.isBlank()) {
                    if (status == 404) logger.debug("AniList 未直接匹配到作品，将尝试别名回退");
                    else logger.warn("AniList 请求失败，status={}", status);
                    return null;
                }
                JSONObject root = JSON.parseObject(body);
                if (root.getJSONArray("errors") != null) {
                    logger.warn("AniList 返回 GraphQL 错误: {}", root.getJSONArray("errors"));
                    return null;
                }
                return root.getJSONObject("data");
            }
        } catch (Exception e) {
            logger.warn("AniList 请求异常: {}", e.getMessage());
            return null;
        }
    }

    private static String downloadCover(JSONObject media, String prefix) {
        JSONObject cover = media.getJSONObject("coverImage");
        if (cover == null) return null;
        return WebImageUtils.download(firstNonBlank(cover.getString("extraLarge"), cover.getString("large")), prefix);
    }

    private static String title(JSONObject media) {
        JSONObject title = object(media, "title");
        return firstNonBlank(title.getString("native"), title.getString("english"), title.getString("romaji"), "未知作品");
    }

    private static String studios(JSONObject media) {
        JSONObject studios = media.getJSONObject("studios");
        JSONArray nodes = studios == null ? null : studios.getJSONArray("nodes");
        if (nodes == null) return "";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            String name = nodes.getJSONObject(i).getString("name");
            if (name != null && !name.isBlank()) names.add(name);
        }
        return String.join(" / ", names);
    }

    private static String staff(JSONObject media) {
        JSONObject staff = media.getJSONObject("staff");
        JSONArray edges = staff == null ? null : staff.getJSONArray("edges");
        if (edges == null) return "";
        List<String> values = new ArrayList<>();
        for (int i = 0; i < edges.size() && values.size() < 8; i++) {
            JSONObject edge = edges.getJSONObject(i);
            JSONObject node = edge.getJSONObject("node");
            JSONObject name = node == null ? null : node.getJSONObject("name");
            String fullName = name == null ? "" : firstNonBlank(name.getString("full"), name.getString("native"));
            if (!fullName.isBlank()) values.add(edge.getString("role") + "：" + fullName);
        }
        return String.join("\n", values);
    }

    private static String cast(JSONObject media) {
        JSONObject characters = media.getJSONObject("characters");
        JSONArray edges = characters == null ? null : characters.getJSONArray("edges");
        if (edges == null) return "";
        List<String> values = new ArrayList<>();
        for (int i = 0; i < edges.size() && values.size() < 8; i++) {
            JSONObject edge = edges.getJSONObject(i);
            JSONObject node = edge.getJSONObject("node");
            JSONObject characterName = node == null ? null : node.getJSONObject("name");
            String character = characterName == null ? ""
                    : firstNonBlank(characterName.getString("native"), characterName.getString("full"));
            JSONArray voiceActors = edge.getJSONArray("voiceActors");
            JSONObject actorNode = voiceActors == null || voiceActors.isEmpty() ? null : voiceActors.getJSONObject(0);
            JSONObject actorName = actorNode == null ? null : actorNode.getJSONObject("name");
            String actor = actorName == null ? ""
                    : firstNonBlank(actorName.getString("native"), actorName.getString("full"));
            if (!character.isBlank()) {
                String role = "MAIN".equals(edge.getString("role")) ? "主角" : "配角";
                values.add(character + (actor.isBlank() ? "" : "：" + actor) + "（" + role + "）");
            }
        }
        return String.join("\n", values);
    }

    private static String cleanDescription(String value) {
        if (value == null || value.isBlank()) return "暂无简介";
        String plain = Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
        return plain.length() > 560 ? plain.substring(0, 557) + "..." : plain;
    }

    private static String season(JSONObject media) {
        Integer year = media.getInteger("seasonYear");
        String season = translate(media.getString("season"));
        if (year == null && season.isBlank()) return "未知";
        return (year == null ? "" : year + " ") + season;
    }

    private static String translate(String value) {
        if (value == null) return "未知";
        return switch (value) {
            case "RELEASING" -> "连载中";
            case "FINISHED" -> "已完结";
            case "NOT_YET_RELEASED" -> "未播出";
            case "CANCELLED" -> "已取消";
            case "HIATUS" -> "暂停";
            case "TV" -> "电视动画";
            case "TV_SHORT" -> "电视短篇";
            case "MOVIE" -> "剧场版";
            case "SPECIAL" -> "特别篇";
            case "OVA", "ONA" -> value;
            case "MANGA" -> "漫画";
            case "LIGHT_NOVEL" -> "轻小说";
            case "ORIGINAL" -> "原创";
            case "VIDEO_GAME" -> "游戏";
            case "WINTER" -> "冬季";
            case "SPRING" -> "春季";
            case "SUMMER" -> "夏季";
            case "FALL" -> "秋季";
            default -> value.replace('_', ' ').toLowerCase();
        };
    }

    private static String join(JSONArray values, int limit) {
        if (values == null) return "";
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, values.size()); i++) result.add(values.getString(i));
        return String.join(" / ", result);
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) result.add(value);
        return String.join(separator, result);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private static JSONObject object(JSONObject parent, String key) {
        JSONObject child = parent == null ? null : parent.getJSONObject(key);
        return child == null ? new JSONObject() : child;
    }

    private static String formatTime(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(ZONE).format(DATE_TIME);
    }
}