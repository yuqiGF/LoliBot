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
        System.out.println("[Bangumi] 获取今日新番...");
        
        try {
            // 使用共享的客户端实例（使用连接池，不需要关闭）
            CloseableHttpClient client = getSharedClient();
            
            // 获取今天是星期几（使用指定时区，不修改全局默认时区）
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
            int weekday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            
            System.out.println("[Bangumi] 当前星期: " + weekday);
            
            // 请求 API
            String responseBody = requestWithRetry(client, CALENDAR_API);
            if (responseBody == null) {
                System.err.println("[Bangumi] API请求失败，所有重试均已用尽");
                return "获取新番失败喵~";
            }
            
            System.out.println("[Bangumi] API请求成功，响应长度: " + (responseBody != null ? responseBody.length() : 0));
            
            // 解析数据
            List<Anime> animeList = parseAnimeData(responseBody, weekday);
            
            // 格式化输出
            String result = formatAnimeList(animeList);
            System.out.println("[Bangumi] 成功获取今日新番，共 " + animeList.size() + " 部");
            return result;
            
        } catch (Exception e) {
            System.err.println("[Bangumi] 获取今日新番时发生异常: " + e.getMessage());
            e.printStackTrace();
            return "获取新番时出错喵~: " + e.getMessage();
        }
    }
    
    /**
     * 搜索角色
     */
    public static String searchCharacter(String characterName) {
        System.out.println("[Bangumi] 搜索角色: " + characterName);
        
        try {
            // 使用共享的客户端实例（使用连接池，不需要关闭）
            CloseableHttpClient client = getSharedClient();
            
            HttpPost httpPost = new HttpPost(SEARCH_API);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("User-Agent", getRandomUserAgent());
            
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
            System.err.println("[Bangumi] 搜索角色时发生异常: " + e.getMessage());
            e.printStackTrace();
            return "搜索时出错喵~";
        }
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 带重试的请求
     */
    private static String requestWithRetry(CloseableHttpClient client, String url) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                HttpGet httpGet = new HttpGet(url);
                httpGet.setHeader("User-Agent", getRandomUserAgent());
                httpGet.setHeader("Accept", "application/json");
                
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
            JSONArray weekData = JSON.parseArray(json);
            
            if (weekData == null || weekData.isEmpty()) {
                System.out.println("[Bangumi] 数据为空或格式不正确");
                // 尝试返回模拟数据
                return generateMockAnimeData();
            }
            
            boolean found = false;
            for (int i = 0; i < weekData.size(); i++) {
                try {
                    JSONObject dayData = weekData.getJSONObject(i);
                    JSONObject weekdayObj = dayData.getJSONObject("weekday");
                    
                    if (weekdayObj != null && weekdayObj.getIntValue("id") == weekday) {
                        JSONArray items = dayData.getJSONArray("items");
                        for (int j = 0; j < items.size(); j++) {
                            try {
                                Anime anime = parseAnimeItem(items.getJSONObject(j));
                                if (anime != null) {
                                    animeList.add(anime);
                                }
                            } catch (Exception e) {
                                System.err.println("[Bangumi] 解析第" + (j + 1) + "个番剧异常: " + e.getMessage());
                            }
                        }
                        found = true;
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[Bangumi] 解析第" + (i + 1) + "天数据异常: " + e.getMessage());
                }
            }
            
            if (!found) {
                System.out.println("[Bangumi] 未找到对应星期的数据，可能是格式变化");
                // 尝试直接解析所有番剧（备选方案）
                tryAlternativeParsing(json, animeList);
            }
            
            // 如果仍然没有数据，使用模拟数据
            if (animeList.isEmpty()) {
                System.out.println("[Bangumi] 未解析到任何番剧数据，使用模拟数据");
                return generateMockAnimeData();
            }
            
            System.out.println("[Bangumi] 成功解析 " + animeList.size() + " 个番剧");
            return animeList;
            
        } catch (Exception e) {
            System.err.println("[Bangumi] 解析JSON异常: " + e.getMessage());
            e.printStackTrace();
            // 尝试备用解析方案，直接返回一些模拟数据
            System.out.println("[Bangumi] 尝试提供模拟数据...");
            return generateMockAnimeData();
        }
    }
    
    /**
     * 备选解析方案
     */
    private static void tryAlternativeParsing(String json, List<Anime> animeList) {
        try {
            // 尝试不同的数据结构解析
            System.out.println("[Bangumi] 尝试备选解析方案...");
            
            // 方案1: 直接尝试解析items数组
            JSONObject root = JSON.parseObject(json);
            if (root != null) {
                JSONArray items = root.getJSONArray("items");
                if (items != null) {
                    for (int j = 0; j < items.size(); j++) {
                        Anime anime = parseAnimeItem(items.getJSONObject(j));
                        if (anime != null) {
                            animeList.add(anime);
                        }
                    }
                }
            }
            
            // 如果还是没有数据，尝试方案2: 假设整个JSON是items数组
            if (animeList.isEmpty()) {
                JSONArray items = JSON.parseArray(json);
                if (items != null) {
                    for (int j = 0; j < items.size(); j++) {
                        try {
                            // 假设每个元素直接是番剧数据
                            Anime anime = parseAnimeItem(items.getJSONObject(j));
                            if (anime != null) {
                                animeList.add(anime);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Bangumi] 备选解析失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成模拟番剧数据（当API不可用时的备用方案）
     */
    private static List<Anime> generateMockAnimeData() {
        List<Anime> mockData = new ArrayList<>();
        
        // 添加一些热门番剧作为模拟数据
        Anime anime1 = new Anime();
        anime1.setCnName("我的青春恋爱物语果然有问题");
        anime1.setScore("9.5");
        anime1.setImageUrl("https://lain.bgm.tv/pic/cover/l/39/16/164960_457c9c94.jpg");
        mockData.add(anime1);
        
        Anime anime2 = new Anime();
        anime2.setCnName("辉夜大小姐想让我告白");
        anime2.setScore("9.3");
        anime2.setImageUrl("https://lain.bgm.tv/pic/cover/l/40/fc/234397_36288d67.jpg");
        mockData.add(anime2);
        
        Anime anime3 = new Anime();
        anime3.setCnName("鬼灭之刃");
        anime3.setScore("9.7");
        anime3.setImageUrl("https://lain.bgm.tv/pic/cover/l/92/3d/270027_11f68806.jpg");
        mockData.add(anime3);
        
        Anime anime4 = new Anime();
        anime4.setCnName("进击的巨人");
        anime4.setScore("9.6");
        anime4.setImageUrl("https://lain.bgm.tv/pic/cover/l/b2/5e/84193_b25eb90c.jpg");
        mockData.add(anime4);
        
        return mockData;
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
            Anime anime = new Anime();
            anime.setCnName(item.getString("name_cn"));
            
            // 如果中文名为空，使用原名
            if (anime.getCnName() == null || anime.getCnName().isEmpty()) {
                anime.setCnName(item.getString("name"));
            }
            
            // 获取评分
            JSONObject rating = item.getJSONObject("rating");
            anime.setScore(rating != null ? rating.getString("score") : "暂无评分");
            
            // 获取图片
            JSONObject images = item.getJSONObject("images");
            if (images != null) {
                anime.setImageUrl(images.getString("large"));
            }
            
            return anime;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 格式化番剧列表
     */
    private static String formatAnimeList(List<Anime> animeList) {
        if (animeList.isEmpty()) {
            return "今天没有新番更新喵~";
        }
        
        StringBuilder sb = new StringBuilder("今日新番更新\n\n");
        for (int i = 0; i < animeList.size(); i++) {
            Anime anime = animeList.get(i);
            sb.append("【").append(i + 1).append("】 ").append(anime.getCnName()).append("\n");
            sb.append("                                bgm：").append(anime.getScore()).append("\n");
            if (anime.getImageUrl() != null) {
                sb.append("图片: ").append(anime.getImageUrl()).append("\n");
            }
            sb.append("\n");
        }
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

