package com.bot.utils.crawler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bot.utils.common.HttpClientPool;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WakaTimeClient {

    private static final String WAKA_BASE = "https://wakatime.com/api/v1";

    private final String apiKey;
    private final String authHeader;

    public WakaTimeClient(String apiKey) {
        this.apiKey = apiKey;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取今日编程时间
     */
    public String getTodayStats() {
        JSONObject data = fetch("/users/current/all_time_since_today");
        if (data == null) return null;

        JSONObject today = data.getJSONObject("data");
        return today.getString("text");
    }

    /**
     * 获取最近7天统计
     */
    public String getWeeklyStats() {
        JSONObject data = fetch("/users/current/stats/last_7_days");
        if (data == null) return null;

        JSONObject stats = data.getJSONObject("data");
        StringBuilder sb = new StringBuilder();
        sb.append("=== 最近7天编程统计 ===\n");

        String total = stats.getString("human_readable_total");
        String dailyAvg = stats.getString("human_readable_daily_average");
        sb.append("总时长: ").append(total != null ? total : "暂无数据").append("\n");
        sb.append("日均: ").append(dailyAvg != null ? dailyAvg : "暂无数据").append("\n");

        // 语言排行
        JSONArray languages = stats.getJSONArray("languages");
        if (languages != null && !languages.isEmpty()) {
            sb.append("\n语言排行:\n");
            int count = Math.min(languages.size(), 5);
            for (int i = 0; i < count; i++) {
                JSONObject lang = languages.getJSONObject(i);
                sb.append("  ").append(i + 1).append(". ")
                        .append(lang.getString("name"))
                        .append(" — ").append(lang.getString("text"))
                        .append(" (").append(String.format("%.1f", lang.getDouble("percent"))).append("%)\n");
            }
        }

        // 编辑器排行
        JSONArray editors = stats.getJSONArray("editors");
        if (editors != null && !editors.isEmpty()) {
            sb.append("\n编辑器:\n");
            int count = Math.min(editors.size(), 3);
            for (int i = 0; i < count; i++) {
                JSONObject editor = editors.getJSONObject(i);
                sb.append("  ").append(editor.getString("name"))
                        .append(" — ").append(editor.getString("text"))
                        .append(" (").append(String.format("%.1f", editor.getDouble("percent"))).append("%)\n");
            }
        }

        // 最佳工作日
        JSONObject bestDay = stats.getJSONObject("best_day");
        if (bestDay != null) {
            sb.append("\n最佳工作日: ").append(bestDay.getString("date"))
                    .append(" (").append(bestDay.getString("text")).append(")");
        }

        return sb.toString();
    }

    private JSONObject fetch(String path) {
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpGet get = new HttpGet(WAKA_BASE + path);
            get.setHeader("Authorization", authHeader);
            get.setHeader("User-Agent", HttpClientPool.getRandomUserAgent());

            String body;
            try (var response = client.execute(get)) {
                body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            }

            if (body == null || body.isEmpty()) return null;
            return JSON.parseObject(body);
        } catch (Exception e) {
            return null;
        }
    }
}
