package com.bot.utils.crawler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bot.model.Anime;
import com.bot.utils.common.HttpClientPool;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Bangumi 番剧数据爬虫
 * 提供今日新番查询和角色搜索功能
 */
public class BangumiCrawler {
    
    private static final int MAX_RETRIES = 3;
    private static final String CALENDAR_API = "https://api.bgm.tv/calendar";
    private static final String SEARCH_API = "https://api.bgm.tv/v0/search/characters";
    private static final String BANGUMI_TOKEN = "D0D7F18A7055D2BF97C2B49F7460D26F";
    
    // 共享的 HTTP 客户端实例（使用连接池，不需要关闭）
    private static volatile CloseableHttpClient sharedClient;
    
    // 模拟真实浏览器的请求头
    private static final List<String> USER_AGENTS;
    
    static {
        USER_AGENTS = new ArrayList<>();
        USER_AGENTS.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        USER_AGENTS.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0");
        USER_AGENTS.add("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Safari/605.1.15");
        USER_AGENTS.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36 Edg/118.0.2088.61");
    }
    
    /**
     * 获取共享的 HTTP 客户端实例
     */
    private static CloseableHttpClient getSharedClient() {
        if (sharedClient == null) {
            synchronized (BangumiCrawler.class) {
                if (sharedClient == null) {
                    sharedClient = HttpClientPool.createClient();
                }
            }
        }
        return sharedClient;
    }
    
    /**
     * 获取今日新番
     */
    public static String getTodayAnime() {
        try {
            // 使用共享的客户端实例（使用连接池，不需要关闭）
            CloseableHttpClient client = getSharedClient();
            
            // 获取今天是星期几（使用指定时区，不修改全局默认时区）
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            
            // 转换Java的星期表示到Bangumi的星期表示
            // Java: 1(周日), 2(周一), ..., 7(周六)
            // Bangumi: 1(周一), 2(周二), ..., 7(周日)
            int weekday;
            if (dayOfWeek == 1) { // 周日
                weekday = 7;
            } else {
                weekday = dayOfWeek - 1;
            }
            
            // 请求 API
            String responseBody = requestWithRetry(client, CALENDAR_API);
            if (responseBody == null) {
                return "获取新番失败喵~";
            }
            
            // 解析数据
            List<Anime> animeList = parseAnimeData(responseBody, weekday);
            
            // 按评分降序排序番剧列表（已注释掉，直接返回所有番剧）
            // sortAnimeByScoreDesc(animeList);
            
            // 格式化输出
            String result = formatAnimeList(animeList);
            return result;
            
        } catch (Exception e) {
            return "获取新番时出错喵~";
        }
    }
    
    /**
     * 按评分降序排序番剧列表
     */
    private static void sortAnimeByScoreDesc(List<Anime> animeList) {
        animeList.sort((a1, a2) -> {
            try {
                // 尝试解析评分
                Double score1 = parseScore(a1.getScore());
                Double score2 = parseScore(a2.getScore());
                
                // 降序排序，高分在前
                return score2.compareTo(score1);
            } catch (Exception e) {
                // 排序出错时保持原顺序
                return 0;
            }
        });
    }
    
    /**
     * 解析评分为Double
     * 处理"暂无评分"、"null"等特殊情况
     */
    private static Double parseScore(String scoreStr) {
        if (scoreStr == null || scoreStr.isEmpty() || "null".equals(scoreStr) || "暂无评分".equals(scoreStr) || "解析错误".equals(scoreStr)) {
            return 0.0; // 无评分的番剧排在最后
        }
        try {
            return Double.parseDouble(scoreStr);
        } catch (NumberFormatException e) {
            return 0.0; // 解析失败的评分视为0
        }
    }
    
    /**
     * 搜索角色
     */
    public static String searchCharacter(String characterName) {
        try {
            // 使用共享的客户端实例（使用连接池，不需要关闭）
            CloseableHttpClient client = getSharedClient();
            
            HttpPost httpPost = new HttpPost(SEARCH_API);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("User-Agent", getRandomUserAgent());
            httpPost.setHeader("Authorization", "Bearer " + BANGUMI_TOKEN);
            
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("keyword", characterName);
            requestBody.put("filter", new JSONObject().fluentPut("nsfw", false));
            
            httpPost.setEntity(new StringEntity(requestBody.toJSONString(), StandardCharsets.UTF_8));
            
            // 执行请求
            String responseBody = executeRequest(client, httpPost);
            if (responseBody == null) {
                return "搜索失败喵~";
            }
            
            // 解析结果
            return parseCharacterInfo(responseBody);
            
        } catch (Exception e) {
            return "搜索时出错喵~";
        }
    }

    
    /**
     * 带重试的请求
     */
    private static String requestWithRetry(CloseableHttpClient client, String url) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                HttpGet httpGet = new HttpGet(url);
                httpGet.setHeader("User-Agent", getRandomUserAgent());
                httpGet.setHeader("Accept", "application/json");
                httpGet.setHeader("Authorization", "Bearer " + BANGUMI_TOKEN);
                
                System.out.println("[Bangumi] 尝试请求 (第 " + (i + 1) + "/" + MAX_RETRIES + " 次): " + url);
                String result = executeRequest(client, httpGet);
                if (result != null && !result.isEmpty()) {
                    System.out.println("[Bangumi] 请求成功");
                    return result;
                }
                
                // 重试延迟
                if (i < MAX_RETRIES - 1) {
                    long delay = HttpClientPool.calculateRetryDelay(i + 1);
                    System.out.println("[Bangumi] 请求失败，等待 " + delay + "ms 后重试...");
                    Thread.sleep(delay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[Bangumi] 重试被中断");
                return null;
            } catch (Exception e) {
                System.err.println("[Bangumi] 请求异常 (第 " + (i + 1) + "/" + MAX_RETRIES + " 次): " + e.getMessage());
                if (i < MAX_RETRIES - 1) {
                    try {
                        long delay = HttpClientPool.calculateRetryDelay(i + 1);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        System.err.println("[Bangumi] 所有重试均失败");
        return null;
    }
    
    /**
     * 执行 HTTP 请求
     */
    private static String executeRequest(CloseableHttpClient client, org.apache.http.client.methods.HttpUriRequest request) {
        HttpResponse response = null;
        try {
            response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode >= 200 && statusCode < 300) {
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            } else {
                System.err.println("[Bangumi] HTTP错误: " + statusCode);
                // 消费响应实体以确保连接可以被重用
                if (response.getEntity() != null) {
                    EntityUtils.consume(response.getEntity());
                }
                return null;
            }
        } catch (IOException e) {
            System.err.println("[Bangumi] 请求异常: " + e.getMessage());
            e.printStackTrace();
            // 如果响应存在，确保消费实体
            if (response != null && response.getEntity() != null) {
                try {
                    EntityUtils.consume(response.getEntity());
                } catch (IOException consumeEx) {
                    // 忽略消费异常
                }
            }
            return null;
        }
    }
    
    /**
     * 解析番剧数据
     */
    private static List<Anime> parseAnimeData(String json, int weekday) {
        List<Anime> animeList = new ArrayList<>();
        
        try {
            // 打印原始响应的前200个字符，帮助调试
            System.out.println("[Bangumi] 原始响应前200字符: " + (json != null ? json.substring(0, Math.min(200, json.length())) : "null"));
            
            // 解析为JSONArray
            JSONArray weekData = JSON.parseArray(json);
            
            if (weekData == null || weekData.isEmpty()) {
                System.out.println("[Bangumi] 数据为空或格式不正确");
                return animeList; // 返回空列表而不是模拟数据
            }
            
            System.out.println("[Bangumi] 成功解析为JSONArray，长度: " + weekData.size());
            
            boolean found = false;
            for (int i = 0; i < weekData.size(); i++) {
                try {
                    JSONObject dayData = weekData.getJSONObject(i);
                    JSONObject weekdayObj = dayData.getJSONObject("weekday");
                    
                    // 兼容不同的星期表示方式
                    if (weekdayObj != null) {
                        int dayId = weekdayObj.getIntValue("id");
                        System.out.println("[Bangumi] 检测到星期ID: " + dayId + ", 当前需要星期ID: " + weekday);
                        
                        if (dayId == weekday) {
                            JSONArray items = dayData.getJSONArray("items");
                            System.out.println("[Bangumi] 找到对应星期的数据，包含 " + items.size() + " 部番剧");
                            
                            for (int j = 0; j < items.size(); j++) {
                                try {
                                    Anime anime = parseAnimeItem(items.getJSONObject(j));
                                    if (anime != null) {
                                        animeList.add(anime);
                                        System.out.println("[Bangumi] 成功添加番剧: " + anime.getCnName());
                                    }
                                } catch (Exception e) {
                                    System.err.println("[Bangumi] 解析第" + (j + 1) + "个番剧异常: " + e.getMessage());
                                }
                            }
                            found = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[Bangumi] 解析第" + (i + 1) + "天数据异常: " + e.getMessage());
                }
            }
            
            if (!found) {
                System.out.println("[Bangumi] 未找到对应星期的数据，可能是格式变化");
                // 尝试备选解析方案
                tryAlternativeParsing(json, animeList, weekday);
            }
            
            System.out.println("[Bangumi] 最终解析到 " + animeList.size() + " 部番剧");
            return animeList;
            
        } catch (Exception e) {
            System.err.println("[Bangumi] 解析JSON异常: " + e.getMessage());
            e.printStackTrace();
            return animeList; // 返回空列表而不是模拟数据
        }
    }
    
    /**
     * 备选解析方案
     */
    private static void tryAlternativeParsing(String json, List<Anime> animeList, int targetWeekday) {
        try {
            // 尝试不同的数据结构解析
            System.out.println("[Bangumi] 尝试备选解析方案...");
            
            // 方案1: 遍历所有天的数据，查找匹配的星期
            JSONArray weekData = JSON.parseArray(json);
            if (weekData != null) {
                for (int i = 0; i < weekData.size(); i++) {
                    try {
                        JSONObject dayData = weekData.getJSONObject(i);
                        // 尝试直接获取air_weekday字段（针对items中的每个番剧）
                        JSONArray items = dayData.getJSONArray("items");
                        if (items != null) {
                            for (int j = 0; j < items.size(); j++) {
                                try {
                                    JSONObject item = items.getJSONObject(j);
                                    if (item.getIntValue("air_weekday") == targetWeekday) {
                                        Anime anime = parseAnimeItem(item);
                                        if (anime != null) {
                                            animeList.add(anime);
                                            System.out.println("[Bangumi] 备选方案添加番剧: " + anime.getCnName());
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("[Bangumi] 备选方案解析第" + (j + 1) + "个番剧异常: " + e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Bangumi] 备选方案解析第" + (i + 1) + "天数据异常: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Bangumi] 备选解析失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取随机User-Agent
     */
    private static String getRandomUserAgent() {
        return USER_AGENTS.get(new Random().nextInt(USER_AGENTS.size()));
    }
    
    /**
     * 随机延迟，模拟人类行为
     */
    private static void randomDelay(int minMs, int maxMs) throws InterruptedException {
        int delay = minMs + new Random().nextInt(maxMs - minMs + 1);
        Thread.sleep(delay);
    }
    
    /**
     * 指数退避延迟计算
     */
    private static int calculateExponentialDelay(int attempt) {
        // 基础延迟1000ms，指数增长，加上随机抖动
        int baseDelay = 1000;
        int maxDelay = 10000;
        int jitter = new Random().nextInt(500);
        
        int delay = (int) Math.min(maxDelay, baseDelay * Math.pow(2, attempt - 1) + jitter);
        return delay;
    }
    
    /**
     * 解析单个番剧项
     */
    private static Anime parseAnimeItem(JSONObject item) {
        try {
            System.out.println("[Bangumi] 开始解析番剧项: " + item.toJSONString());
            
            Anime anime = new Anime();
            
            // 尝试多种可能的名称字段
            String cnName = null;
            String name = null;
            
            // 首先尝试name_cn字段
            try {
                cnName = item.getString("name_cn");
                System.out.println("[Bangumi] 获取name_cn: " + cnName);
            } catch (Exception e) {
                System.err.println("[Bangumi] 无法获取name_cn: " + e.getMessage());
            }
            
            // 尝试name字段
            try {
                name = item.getString("name");
                System.out.println("[Bangumi] 获取name: " + name);
            } catch (Exception e) {
                System.err.println("[Bangumi] 无法获取name: " + e.getMessage());
            }
            
            // 设置中文名，如果为空则使用原名
            if (cnName != null && !cnName.isEmpty() && !"null".equals(cnName)) {
                anime.setCnName(cnName);
            } else if (name != null && !name.isEmpty() && !"null".equals(name)) {
                anime.setCnName(name);
            } else {
                anime.setCnName("未知番剧");
                System.out.println("[Bangumi] 无法获取番剧名称，设置为'未知番剧'");
            }
            
            // 获取评分
            String score = "暂无评分";
            try {
                JSONObject rating = item.getJSONObject("rating");
                if (rating != null) {
                    // 尝试多种可能的评分字段
                    if (rating.containsKey("score")) {
                        score = rating.getBigDecimal("score").toString();
                    } else if (rating.containsKey("total")) {
                        score = rating.getString("total");
                    }
                    System.out.println("[Bangumi] 获取评分: " + score);
                }
            } catch (Exception e) {
                System.err.println("[Bangumi] 无法获取评分: " + e.getMessage());
            }
            
            // 确保评分为有效字符串
            if (score == null || score.isEmpty() || "null".equals(score)) {
                score = "null";
            }
            anime.setScore(score);
            
            // 尝试多种可能的图片字段
            String imageUrl = null;
            
            try {
                // 尝试images字段
                JSONObject images = item.getJSONObject("images");
                if (images != null) {
                    // 尝试不同尺寸的图片
                    if (images.containsKey("large")) {
                        imageUrl = images.getString("large");
                    } else if (images.containsKey("medium")) {
                        imageUrl = images.getString("medium");
                    } else if (images.containsKey("small")) {
                        imageUrl = images.getString("small");
                    }
                    System.out.println("[Bangumi] 获取图片URL: " + imageUrl);
                }
            } catch (Exception e) {
                System.err.println("[Bangumi] 无法获取图片URL: " + e.getMessage());
            }
            
            // 设置图片URL
            if (imageUrl != null && !imageUrl.isEmpty() && !"null".equals(imageUrl)) {
                // 去除可能的空格和换行符
                imageUrl = imageUrl.trim();
                anime.setImageUrl(imageUrl);
            }
            
            System.out.println("[Bangumi] 解析完成: 名称='" + anime.getCnName() + "', 评分='" + anime.getScore() + "'");
            return anime;
        } catch (Exception e) {
            System.err.println("[Bangumi] 解析番剧项异常: " + e.getMessage());
            // 返回带有错误信息的番剧对象，而不是null
            Anime anime = new Anime();
            anime.setCnName("解析错误的番剧");
            anime.setScore("解析错误");
            return anime;
        }
    }
    
    /**
     * 格式化番剧列表（只显示评分最高的前10部）
     */
    private static String formatAnimeList(List<Anime> animeList) {
        if (animeList.isEmpty()) {
            return "今天没有新番更新喵~";
        }
        
        StringBuilder sb = new StringBuilder("今日新番更新\n\n");
        
        // 直接显示所有番剧（不再限制为前10部）
        int displayCount = animeList.size();
        
        for (int i = 0; i < displayCount; i++) {
            Anime anime = animeList.get(i);
            sb.append("【").append(i + 1).append("】 ").append(anime.getCnName()).append("\n");
            sb.append("                                bgm：").append(anime.getScore()).append("\n");
            if (anime.getImageUrl() != null) {
                // 彻底清理图片URL中的空格和反引号
                String cleanImageUrl = anime.getImageUrl().trim() // 先去除首尾空格
                                          .replaceAll("[`\s]+", ""); // 使用正则表达式移除所有反引号和空格
                sb.append("图片: ").append(cleanImageUrl).append("\n");
            }
            sb.append("\n");
        }
        
        // 显示总数信息（适用于所有情况）
        sb.append("共").append(animeList.size()).append("部新番\n\n");
        
        sb.append("到点了，该看番了喵🥰🥰🥰~");
        return sb.toString();
    }
    
    /**
     * 解析角色信息
     */
    private static String parseCharacterInfo(String json) {
        JSONObject response = JSON.parseObject(json);
        JSONArray data = response.getJSONArray("data");
        
        if (data == null || data.isEmpty()) {
            return "没有找到角色信息喵~";
        }
        
        JSONObject character = data.getJSONObject(0);
        StringBuilder info = new StringBuilder();
        
        info.append("角色名: ").append(character.getString("name")).append("\n");
        
        // 中文名
        String chineseName = extractValue(character, "简体中文名");
        if (!chineseName.isEmpty()) {
            info.append("中文名: ").append(chineseName).append("\n");
        }
        
        // 性别
        String gender = character.getString("gender");
        if (gender != null) {
            info.append("性别: ").append(gender.equals("male") ? "男" : "女").append("\n");
        }
        
        // 简介
        String summary = character.getString("summary");
        if (summary != null && !summary.isEmpty()) {
            info.append("简介: ").append(summary.substring(0, Math.min(100, summary.length()))).append("...\n");
        }
        
        // 图片
        JSONObject images = character.getJSONObject("images");
        if (images != null) {
            String imageUrl = images.getString("large");
            if (imageUrl != null) {
                info.append("图片: ").append(imageUrl).append("\n");
            }
        }
        
        return info.toString();
    }
    
    /**
     * 从 infobox 提取值
     */
    private static String extractValue(JSONObject character, String key) {
        JSONArray infobox = character.getJSONArray("infobox");
        if (infobox != null) {
            for (int i = 0; i < infobox.size(); i++) {
                JSONObject item = infobox.getJSONObject(i);
                if (key.equals(item.getString("key"))) {
                    Object value = item.get("value");
                    if (value instanceof String) {
                        return (String) value;
                    } else if (value instanceof JSONArray) {
                        JSONArray values = (JSONArray) value;
                        if (!values.isEmpty()) {
                            return values.getJSONObject(0).getString("v");
                        }
                    }
                }
            }
        }
        return "";
    }
}

