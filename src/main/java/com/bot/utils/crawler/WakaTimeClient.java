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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WakaTime 官方 API 客户端。
 *
 * <p>今日统计用 durations 读取尚未进入缓存的实时数据，并以 summaries 补全；
 * 周统计自行聚合七个自然日，保证日期、时区和日均口径一致。</p>
 */
public class WakaTimeClient {
    private static final Logger logger = LoggerFactory.getLogger(WakaTimeClient.class);
    private static final String BASE = "https://wakatime.com/api/v1";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final String authHeader;
    private final ZoneId zoneId;

    public WakaTimeClient(String apiKey) {
        this(apiKey, "Asia/Shanghai");
    }

    public WakaTimeClient(String apiKey, String timezone) {
        authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));
        ZoneId parsed;
        try {
            parsed = ZoneId.of(timezone);
        } catch (Exception ignored) {
            parsed = ZoneId.of("Asia/Shanghai");
        }
        zoneId = parsed;
    }

    /** 查询今天 00:00 到当前时刻，绝不再使用含义为“账号累计”的 all_time_since_today。 */
    public String getTodayStats() {
        LocalDate today = LocalDate.now(zoneId);
        DaySummary cached = fetchDaySummary(today);
        SliceSummary projects = fetchDurations(today, "project");
        SliceSummary languages = fetchDurations(today, "language");
        SliceSummary editors = fetchDurations(today, "editor");
        if (cached == null && projects == null) return null;

        double cachedSeconds = cached == null ? 0 : cached.totalSeconds();
        double realtimeSeconds = projects == null ? 0 : projects.totalSeconds();
        double total = Math.max(cachedSeconds, realtimeSeconds);
        StringBuilder out = new StringBuilder("【WakaTime 今日】\n")
                .append("日期：").append(today).append("（").append(zoneId).append("）\n")
                .append("总时长：").append(formatDuration(total)).append('\n');
        if (total <= 0.5) {
            out.append("状态：今天暂时没有已同步的编程记录\n");
        } else {
            appendRanking(out, "项目", prefer(projects, cached, "project"), total, 3);
            appendRanking(out, "语言", prefer(languages, cached, "language"), total, 5);
            appendRanking(out, "编辑器", prefer(editors, cached, "editor"), total, 3);
        }
        out.append("查询时间：").append(ZonedDateTime.now(zoneId).format(CLOCK));
        return out.toString();
    }

    /** 最近七天包含今天，日均固定按七个自然日计算。 */
    public String getWeeklyStats() {
        LocalDate end = LocalDate.now(zoneId);
        LocalDate start = end.minusDays(6);
        JSONObject root = fetch("/users/current/summaries?start=" + start + "&end=" + end
                + "&timezone=" + encode(zoneId.getId()));
        if (root == null || root.getJSONArray("data") == null) return null;

        JSONArray days = root.getJSONArray("data");
        Map<String, Double> languages = new LinkedHashMap<>();
        Map<String, Double> projects = new LinkedHashMap<>();
        Map<String, Double> editors = new LinkedHashMap<>();
        double total = 0;
        double best = 0;
        String bestDate = "-";
        int activeDays = 0;
        for (int i = 0; i < days.size(); i++) {
            JSONObject day = days.getJSONObject(i);
            double seconds = number(day.getJSONObject("grand_total"), "total_seconds");
            total += seconds;
            if (seconds > 0.5) activeDays++;
            if (seconds > best) {
                best = seconds;
                JSONObject range = day.getJSONObject("range");
                bestDate = range == null ? "-" : shortDate(range.getString("date"));
            }
            merge(languages, day.getJSONArray("languages"));
            merge(projects, day.getJSONArray("projects"));
            merge(editors, day.getJSONArray("editors"));
        }

        StringBuilder out = new StringBuilder("【WakaTime 最近 7 天】\n")
                .append("范围：").append(start).append(" 至 ").append(end).append('\n')
                .append("总时长：").append(formatDuration(total)).append('\n')
                .append("自然日均：").append(formatDuration(total / 7)).append('\n')
                .append("活跃天数：").append(activeDays).append(" / 7\n");
        if (best > 0.5) out.append("最高一天：").append(bestDate).append(" · ").append(formatDuration(best)).append('\n');
        appendRanking(out, "语言", languages, total, 5);
        appendRanking(out, "项目", projects, total, 4);
        appendRanking(out, "编辑器", editors, total, 3);
        return out.toString().trim();
    }

    private DaySummary fetchDaySummary(LocalDate date) {
        JSONObject root = fetch("/users/current/summaries?start=" + date + "&end=" + date
                + "&timezone=" + encode(zoneId.getId()));
        if (root == null) return null;
        JSONArray data = root.getJSONArray("data");
        if (data == null || data.isEmpty()) return new DaySummary(0, Map.of(), Map.of(), Map.of());
        JSONObject day = data.getJSONObject(0);
        return new DaySummary(
                number(day.getJSONObject("grand_total"), "total_seconds"),
                metrics(day.getJSONArray("projects")),
                metrics(day.getJSONArray("languages")),
                metrics(day.getJSONArray("editors"))
        );
    }

    private SliceSummary fetchDurations(LocalDate date, String sliceBy) {
        JSONObject root = fetch("/users/current/durations?date=" + date
                + "&timezone=" + encode(zoneId.getId()) + "&slice_by=" + sliceBy);
        if (root == null) return null;
        Map<String, Double> values = new LinkedHashMap<>();
        double total = 0;
        JSONArray data = root.getJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                double seconds = number(item, "duration");
                String name = item.getString(sliceBy);
                if (name == null || name.isBlank()) name = "其他";
                total += seconds;
                values.merge(name, seconds, Double::sum);
            }
        }
        return new SliceSummary(total, values);
    }

    /** 检查 HTTP 状态和 API 错误字段，避免把错误 JSON 当成正常统计。 */
    private JSONObject fetch(String path) {
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpGet get = new HttpGet(BASE + path);
            get.setHeader("Authorization", authHeader);
            get.setHeader("Accept", "application/json");
            try (CloseableHttpResponse response = client.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                String body = response.getEntity() == null ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (status < 200 || status >= 300 || body.isBlank()) {
                    logger.warn("WakaTime 请求失败，status={}, path={}", status, path);
                    return null;
                }
                JSONObject json = JSON.parseObject(body);
                if (json.containsKey("error") || json.containsKey("errors")) return null;
                return json;
            }
        } catch (Exception e) {
            logger.warn("WakaTime 请求异常，path={}, message={}", path, e.getMessage());
            return null;
        }
    }

    private static Map<String, Double> prefer(SliceSummary realtime, DaySummary cached, String type) {
        if (realtime != null && realtime.totalSeconds() > 0.5) return realtime.values();
        if (cached == null) return Map.of();
        return switch (type) {
            case "project" -> cached.projects();
            case "language" -> cached.languages();
            default -> cached.editors();
        };
    }

    private static Map<String, Double> metrics(JSONArray array) {
        Map<String, Double> result = new LinkedHashMap<>();
        merge(result, array);
        return result;
    }

    private static void merge(Map<String, Double> target, JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            String name = item.getString("name");
            if (name == null || name.isBlank()) name = "其他";
            target.merge(name, number(item, "total_seconds"), Double::sum);
        }
    }

    private static void appendRanking(StringBuilder out, String label, Map<String, Double> values, double total, int limit) {
        if (values == null || values.isEmpty()) return;
        List<Map.Entry<String, Double>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            Map.Entry<String, Double> entry = entries.get(i);
            double percent = total <= 0 ? 0 : entry.getValue() * 100 / total;
            parts.add(entry.getKey() + " " + formatDuration(entry.getValue()) + "（"
                    + String.format(Locale.ROOT, "%.1f", percent) + "%）");
        }
        out.append(label).append("：").append(String.join(" / ", parts)).append('\n');
    }

    private static double number(JSONObject object, String key) {
        if (object == null) return 0;
        Double value = object.getDouble(key);
        return value == null || !Double.isFinite(value) ? 0 : Math.max(0, value);
    }

    private static String formatDuration(double seconds) {
        long value = Math.max(0, Math.round(seconds));
        long hours = value / 3600;
        long minutes = value % 3600 / 60;
        long secs = value % 60;
        if (hours > 0) return hours + "小时" + minutes + "分钟";
        if (minutes > 0) return minutes + "分钟" + secs + "秒";
        return secs + "秒";
    }

    private static String shortDate(String value) {
        return value != null && value.length() >= 10 ? value.substring(0, 10) : "-";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record DaySummary(double totalSeconds, Map<String, Double> projects,
                              Map<String, Double> languages, Map<String, Double> editors) {
    }

    private record SliceSummary(double totalSeconds, Map<String, Double> values) {
    }
}