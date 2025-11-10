package com.bot.utils.crawler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * 萌娘百科爬虫
 * 使用MediaWiki API绕过反爬限制
 */
public class MoeGirlCrawler {
    
    private static final int TIMEOUT = 30000;
    private static final String BASE_URL = "https://mzh.moegirl.org.cn";
    private static final String API_URL = BASE_URL + "/api.php";
    private static final Random random = new Random();
    
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15"
    };
    
    /**
     * 获取角色信息（使用TextExtracts API优化版）
     */
    public static String getInfo(String characterName) {
        if (characterName == null || characterName.trim().isEmpty()) {
            return "请输入要查询的角色名";
        }
        
        System.out.println("[MoeGirl] 查询: " + characterName);
        
        try {
            // 使用MediaWiki API搜索
            String pageTitle = searchPageTitle(characterName);
            if (pageTitle == null) {
                return "未找到相关信息";
            }
            
            System.out.println("[MoeGirl] 找到页面: " + pageTitle);
            
            // 检测是否发生了重定向（标题与查询不完全一致）
            boolean isRedirected = !pageTitle.equalsIgnoreCase(characterName) && 
                                   !pageTitle.replace(" ", "").equalsIgnoreCase(characterName.replace(" ", ""));
            
            // 使用TextExtracts API获取简短介绍和图片
            PageInfo pageInfo = getPageInfo(pageTitle);
            
            if (pageInfo == null) {
                return "获取页面信息失败";
            }
            
            // 格式化输出
            StringBuilder result = new StringBuilder();
            
            // 如果发生重定向，显示提示
            if (isRedirected) {
                result.append("重定向至：【").append(pageTitle).append("】\n\n");
            } else {
                result.append("【").append(pageTitle).append("】\n\n");
            }
            
            // 添加图片URL（如果有）
            if (pageInfo.imageUrl != null && !pageInfo.imageUrl.isEmpty()) {
                result.append("🖼️ 图片：").append(pageInfo.imageUrl).append("\n\n");
            }
            
            // 添加简短介绍
            if (pageInfo.extract != null && !pageInfo.extract.isEmpty()) {
                result.append(pageInfo.extract);
            } else {
                result.append("暂无介绍信息");
            }
            
            // 获取并添加基本信息（从infobox表格提取）
            String basicInfo = extractInfoboxData(pageTitle);
            if (basicInfo != null && !basicInfo.isEmpty()) {
                result.append("\n\n━━━ 基本信息 ━━━\n");
                result.append(basicInfo);
            }
            
            // 在末尾添加页面URL
            String pageUrl = BASE_URL + "/" + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8);
            result.append("\n\n🔗 来源：").append(pageUrl);
            
            return result.toString().trim();
            
        } catch (Exception e) {
            System.err.println("[MoeGirl] 错误: " + e.getMessage());
            e.printStackTrace();
            return "获取信息失败: " + e.getMessage();
        }
    }
    
    /**
     * 页面信息封装类
     */
    private static class PageInfo {
        String extract;      // 简短介绍
        String imageUrl;     // 主图URL
        
        PageInfo(String extract, String imageUrl) {
            this.extract = extract;
            this.imageUrl = imageUrl;
        }
    }
    
    // ==================== API方法 ====================
    
    /**
     * 使用OpenSearch API搜索页面标题
     */
    private static String searchPageTitle(String keyword) throws IOException {
        try {
            Thread.sleep(random.nextInt(500) + 300);
            
            String url = API_URL + "?action=opensearch&format=json&limit=5&search=" + 
                        URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENTS[random.nextInt(USER_AGENTS.length)])
                    .timeout(TIMEOUT)
                    .ignoreContentType(true)
                    .get();
            
            String jsonText = doc.body().text();
            JSONArray jsonArray = JSON.parseArray(jsonText);
            
            if (jsonArray.size() >= 2) {
                JSONArray titles = jsonArray.getJSONArray(1);
                if (titles != null && !titles.isEmpty()) {
                    return titles.getString(0);
                }
            }
            
            return null;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("中断", e);
        }
    }
    
    /**
     * 使用TextExtracts和PageImages API获取页面信息
     * 一次请求同时获取简短介绍和主图
     */
    private static PageInfo getPageInfo(String pageTitle) {
        try {
            Thread.sleep(random.nextInt(300) + 200);
            
            // 构建API URL，同时请求extracts和pageimages
            // 注意：prop参数中的|需要被URL编码为%7C
            String url = API_URL + 
                "?action=query" +
                "&format=json" +
                "&prop=extracts%7Cpageimages" +  // extracts|pageimages，|编码为%7C
                "&exintro=1" +                     // 只获取介绍部分
                "&explaintext=1" +                 // 纯文本格式
                "&exsentences=5" +                 // 限制5句话
                "&piprop=original" +               // 获取原始图片
                "&titles=" + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8);
            
            System.out.println("[MoeGirl] 请求API获取摘要和图片");
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENTS[random.nextInt(USER_AGENTS.length)])
                    .timeout(TIMEOUT)
                    .ignoreContentType(true)
                    .get();
            
            String jsonText = doc.body().text();
            JSONObject json = JSON.parseObject(jsonText);
            
            if (json.containsKey("query")) {
                JSONObject query = json.getJSONObject("query");
                if (query.containsKey("pages")) {
                    JSONObject pages = query.getJSONObject("pages");
                    
                    // 获取第一个页面的信息
                    for (String pageId : pages.keySet()) {
                        JSONObject page = pages.getJSONObject(pageId);
                        
                        // 提取文本摘要
                        String extract = page.getString("extract");
                        if (extract != null) {
                            extract = cleanExtract(extract);
                        }
                        
                        // 提取图片URL
                        String imageUrl = null;
                        if (page.containsKey("original")) {
                            imageUrl = page.getJSONObject("original").getString("source");
                            System.out.println("[MoeGirl] 找到图片: " + imageUrl);
                        }
                        
                        return new PageInfo(extract, imageUrl);
                    }
                }
            }
            
            return null;
            
        } catch (Exception e) {
            System.err.println("[MoeGirl] 获取页面信息失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 清理TextExtracts返回的文本
     */
    private static String cleanExtract(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 移除萌娘百科常见的模板提示文字
        String[] removePatterns = {
            "本条目介绍的是.*?。.*?，请参见.*?。",
            "萌娘百科欢迎您参与完善本条目.*?编辑前请阅读.*?。",
            "欢迎正在阅读这个条目的您协助.*?。",
            "此页面中存在.*?需要进一步审核的内容。",
            "提示：本条目的主题不是.*?。"
        };
        
        for (String pattern : removePatterns) {
            text = text.replaceAll(pattern, "");
        }
        
        // 移除引用标记 [1], [2] 等
        text = text.replaceAll("\\[\\d+\\]", "");
        
        // 移除多余的空行
        text = text.replaceAll("\n{3,}", "\n\n");
        
        return text.trim();
    }
    
    /**
     * 从页面右侧的基本资料表格中提取信息
     */
    private static String extractInfoboxData(String pageTitle) {
        try {
            Thread.sleep(random.nextInt(300) + 200);
            
            // 使用Parse API获取页面HTML
            String url = API_URL + 
                "?action=parse" +
                "&format=json" +
                "&prop=text" +
                "&page=" + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8);
            
            System.out.println("[MoeGirl] 获取基本信息表格");
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENTS[random.nextInt(USER_AGENTS.length)])
                    .timeout(TIMEOUT)
                    .ignoreContentType(true)
                    .get();
            
            String jsonText = doc.body().text();
            JSONObject json = JSON.parseObject(jsonText);
            
            if (!json.containsKey("parse")) {
                return null;
            }
            
            JSONObject parse = json.getJSONObject("parse");
            if (!parse.containsKey("text")) {
                return null;
            }
            
            JSONObject textObj = parse.getJSONObject("text");
            String html = textObj.getString("*");
            
            // 检查是否仍然是重定向页面（有些重定向可能需要手动处理）
            if (html.contains("重定向") && html.length() < 100) {
                System.out.println("[MoeGirl] 检测到重定向页面，HTML内容太短");
                return null;
            }
            
            // MediaWiki API返回的是纯文本格式，不是HTML表格
            // 从纯文本中提取信息框数据
            StringBuilder info = new StringBuilder();
            int count = 0;
            
            // 定义需要提取的字段（按优先级）
            String[] infoKeys = {
                "本名", "别名", "别号", "发色", "瞳色", "身高", "体重", "年龄", "生日", 
                "星座", "血型", "声优", "CV", "萌点", "出身地区", "活动范围", "所属团体",
                "亲属或相关人", "类型", "平台", "开发", "发行", "引擎", "模式", "发行时间",
                "中文名", "日文名", "英文名", "原名", "译名", "罗马音", "作者", "插画", 
                "地区", "连载杂志", "丛书", "出版社", "发表期间", "册数", "话数",
                "作词", "作曲", "编曲", "歌手", "时长", "收录专辑"
            };
            
            // 查找包含infobox信息的文本段落（通常在Art by后面）
            String searchText = html;
            int artByIndex = html.indexOf("Art by");
            if (artByIndex > 0) {
                searchText = html.substring(artByIndex, Math.min(artByIndex + 5000, html.length()));
            }
            
            // 逐行解析文本，查找键值对（按空白字符分割）
            String[] lines = searchText.split("\\s+");
            
            for (int i = 0; i < lines.length - 1 && count < 30; i++) {
                String line = lines[i].trim();
                
                // 检查是否是我们关注的键
                for (String key : infoKeys) {
                    if (line.equals(key)) {
                        // 下一个元素可能是值
                        StringBuilder value = new StringBuilder();
                        int j = i + 1;
                        
                        // 收集值，直到遇到下一个键或特殊标记
                        while (j < lines.length && j < i + 10) {  // 最多向后查找10个元素
                            String nextLine = lines[j].trim();
                            
                            // 检查是否是下一个键
                            boolean isNextKey = false;
                            for (String checkKey : infoKeys) {
                                if (nextLine.equals(checkKey)) {
                                    isNextKey = true;
                                    break;
                                }
                            }
                            
                            if (isNextKey || nextLine.isEmpty() || 
                                nextLine.startsWith("[") || nextLine.startsWith("(")) {
                                break;
                            }
                            
                            if (value.length() > 0) value.append(" ");
                            value.append(nextLine);
                            j++;
                        }
                        
                        String valueStr = value.toString().trim();
                        // 清理值：移除引用标记等
                        valueStr = valueStr.replaceAll("\\[\\d+\\]", "").trim();
                        
                        if (!valueStr.isEmpty() && valueStr.length() < 500) {
                            info.append(key).append("：").append(valueStr).append("\n");
                            count++;
                            break;  // 找到后跳出内层循环
                        }
                    }
                }
            }
            
            System.out.println("[MoeGirl] 提取到 " + count + " 个基本信息字段");
            
            return info.length() > 0 ? info.toString() : null;
            
        } catch (Exception e) {
            System.err.println("[MoeGirl] 提取基本信息失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 清理HTML元素的文本内容
     */
    private static String cleanText(Element element) {
        if (element == null) return "";
        
        // 移除script、style等标签
        element.select("script, style, sup.reference").remove();
        
        String text = element.text().trim();
        
        // 移除引用标记 [1], [2] 等
        text = text.replaceAll("\\[\\d+\\]", "");
        
        // 移除多余空格
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    }
    
    /**
     * 获取页面主图片
     */
    private static String getPageImage(String pageTitle) {
        try {
            Thread.sleep(random.nextInt(300) + 200);
            
            String url = API_URL + "?action=query&format=json&prop=pageimages&piprop=original&titles=" + 
                        URLEncoder.encode(pageTitle, StandardCharsets.UTF_8);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENTS[random.nextInt(USER_AGENTS.length)])
                    .timeout(TIMEOUT)
                    .ignoreContentType(true)
                    .get();
            
            String jsonText = doc.body().text();
            JSONObject json = JSON.parseObject(jsonText);
            
            if (json.containsKey("query")) {
                JSONObject query = json.getJSONObject("query");
                if (query.containsKey("pages")) {
                    JSONObject pages = query.getJSONObject("pages");
                    // 获取第一个页面
                    for (String pageId : pages.keySet()) {
                        JSONObject page = pages.getJSONObject(pageId);
                        if (page.containsKey("original")) {
                            String imageUrl = page.getJSONObject("original").getString("source");
                            System.out.println("[MoeGirl] 找到图片: " + imageUrl);
                            return imageUrl;
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            System.out.println("[MoeGirl] 获取图片失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 使用Parse API获取页面内容
     */
    private static String getPageContent(String pageTitle) throws IOException {
        try {
            Thread.sleep(random.nextInt(500) + 300);
            
            String url = API_URL + "?action=parse&format=json&prop=text&page=" + 
                        URLEncoder.encode(pageTitle, StandardCharsets.UTF_8);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENTS[random.nextInt(USER_AGENTS.length)])
                    .timeout(TIMEOUT)
                    .ignoreContentType(true)
                    .get();
            
            String jsonText = doc.body().text();
            JSONObject json = JSON.parseObject(jsonText);
            
            if (json.containsKey("parse")) {
                JSONObject parse = json.getJSONObject("parse");
                if (parse.containsKey("text")) {
                    Object textObj = parse.get("text");
                    
                    String html = null;
                    if (textObj instanceof JSONObject) {
                        html = ((JSONObject) textObj).getString("*");
                    } else if (textObj instanceof String) {
                        html = (String) textObj;
                    }
                    
                    return html;
                }
            }
            
            return null;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("中断", e);
        }
    }
    
    /**
     * 格式化并提取关键信息
     */
    private static String formatContent(String title, String content, String imageUrl) {
        StringBuilder result = new StringBuilder();
        result.append("【").append(title).append("】\n");
        
        // 如果有图片，添加图片链接
        if (imageUrl != null && !imageUrl.isEmpty()) {
            result.append("🖼️ 图片：").append(imageUrl).append("\n");
        }
        
        result.append("\n");
        
        // 检查是否包含HTML标签
        boolean isHtml = content.contains("<") && (
            content.contains("<p>") || 
            content.contains("<div") || 
            content.contains("<table") ||
            content.contains("<img") ||
            content.contains("<span")
        );
        
        if (!isHtml) {
            // 纯文本格式，直接格式化输出
            return formatPlainText(title, content);
        }
        
        // HTML格式，使用Jsoup解析为HTML片段
        // MediaWiki API返回的是HTML片段，不是完整文档，需要用parseBodyFragment
        Document doc = Jsoup.parseBodyFragment(content);
        Element body = doc.body();
        
        // 提取信息框
        Element infobox = doc.selectFirst("table.infobox, table.moe-infobox, table.wikitable");
        
        if (infobox != null) {
            String info = extractInfobox(infobox);
            if (!info.isEmpty()) {
                result.append("━━━ 基本信息 ━━━\n").append(info).append("\n");
            }
        }
        
        // 提取简介
        String summary = extractSummary(doc);
        if (!summary.isEmpty()) {
            result.append("━━━ 简介 ━━━\n").append(summary);
        }
        
        // 如果HTML解析没有结果，fallback到纯文本解析
        int baseLength = title.length() + (imageUrl != null ? imageUrl.length() + 10 : 0) + 10;
        if (result.length() <= baseLength) {
            // 使用原始content而不是body.text()，因为后者会压缩所有换行符
            // 先提取纯文本信息
            String plainTextResult = formatPlainText(title, content);
            // 如果有图片，添加图片链接
            if (imageUrl != null && !imageUrl.isEmpty()) {
                StringBuilder withImage = new StringBuilder();
                withImage.append("【").append(title).append("】\n");
                withImage.append("🖼️ 图片：").append(imageUrl).append("\n\n");
                // 去掉原始结果中的标题行
                String contentOnly = plainTextResult.substring(plainTextResult.indexOf("】\n\n") + 3);
                withImage.append(contentOnly);
                return withImage.toString();
            }
            return plainTextResult;
        }
        
        return result.length() > title.length() + 10 ? result.toString().trim() : "未找到详细信息";
    }
    
    /**
     * 格式化纯文本内容
     */
    private static String formatPlainText(String title, String text) {
        StringBuilder result = new StringBuilder();
        result.append("【").append(title).append("】\n\n");
        
        // 清理文本
        text = text.trim();
        
        // 1. 提取基本信息（通常在开头部分，包含键值对形式的数据）
        String basicInfo = extractBasicInfo(text);
        if (!basicInfo.isEmpty()) {
            result.append("━━━ 基本信息 ━━━\n").append(basicInfo).append("\n");
        }
        
        // 2. 提取简介（在目录之前的叙述性段落）
        String summary = extractTextSummary(text);
        if (!summary.isEmpty()) {
            result.append("━━━ 简介 ━━━\n").append(summary);
        }
        
        return result.length() > title.length() + 15 ? result.toString().trim() : "未找到详细信息";
    }
    
    /**
     * 提取基本信息（键值对形式）
     */
    private static String extractBasicInfo(String text) {
        StringBuilder info = new StringBuilder();
        
        // 常见的信息字段 - 扩展更多字段
        String[] infoKeys = {
            // 角色信息
            "中文名", "日文名", "英文名", "别名", "罗马音", "本名",
            "发色", "瞳色", "身高", "体重", "年龄", "生日", "星座", "性别", "血型",
            "声优", "CV", "配音", "演员",
            // 作品信息
            "类型", "平台", "开发", "发行", "制作人", "总监", "编剧", "美术", "音乐",
            "模式", "发售日", "引擎", "改编", "原作",
            // 其他
            "所属", "职业", "等级", "出场作品", "登场作品", "萌点", "特征"
        };
        
        // 需要跳过的关键词
        String[] skipKeywords = {
            "[编辑", "编辑源代码", "游戏系统", "角色列表", "世界观",
            "剧情", "开发历程", "评价", "影响", "相关",
            "目录", "参见", "注释", "外部链接", "官方网站"
        };
        
        String[] lines = text.split("\n");
        int infoCount = 0;
        
        // 第一遍：提取明确的键值对
        for (int i = 0; i < lines.length && infoCount < 30; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            // 跳过包含skipKeywords的行
            boolean shouldSkip = false;
            for (String skip : skipKeywords) {
                if (line.contains(skip)) {
                    shouldSkip = true;
                    break;
                }
            }
            if (shouldSkip) continue;
            
            // 检查是否包含信息键
            for (String key : infoKeys) {
                // 查找"键"开头或"键 "的模式
                if (line.startsWith(key) || line.contains(" " + key + " ")) {
                    String value = extractValue(line, key, lines, i, infoKeys);
                    
                    if (value != null && !value.isEmpty() && value.length() < 200) {
                        // 清理value
                        value = cleanValue(value);
                        
                        // 跳过包含skipKeywords的值
                        boolean skipValue = false;
                        for (String skip : skipKeywords) {
                            if (value.contains(skip)) {
                                skipValue = true;
                                break;
                            }
                        }
                        
                        if (!skipValue && !value.isEmpty() && value.length() > 1) {
                            // 避免重复添加相同的键
                            if (!info.toString().contains(key + "：")) {
                                info.append(key).append("：").append(value).append("\n");
                                infoCount++;
                            }
                        }
                    }
                    break;
                }
            }
        }
        
        return info.toString().trim();
    }
    
    /**
     * 从行中提取值
     */
    private static String extractValue(String line, String key, String[] lines, int currentIndex, String[] allKeys) {
        // 尝试多种格式提取值
        
        // 格式1: 键：值 或 键 值
        int keyIndex = line.indexOf(key);
        if (keyIndex >= 0) {
            String after = line.substring(keyIndex + key.length()).trim();
            // 去掉可能的冒号、空格
            after = after.replaceFirst("^[：:\\s]+", "");
            
            if (!after.isEmpty()) {
                // 检查是否在同一行有其他键，如果有则截断
                for (String otherKey : allKeys) {
                    if (!otherKey.equals(key) && after.contains(otherKey)) {
                        int otherKeyIndex = after.indexOf(otherKey);
                        after = after.substring(0, otherKeyIndex).trim();
                        break;
                    }
                }
                return after;
            }
        }
        
        // 格式2: 键在单独一行，值在下一行
        if (line.trim().equals(key) && currentIndex + 1 < lines.length) {
            String nextLine = lines[currentIndex + 1].trim();
            // 确保下一行不是另一个键
            for (String k : allKeys) {
                if (nextLine.startsWith(k)) {
                    return null;
                }
            }
            return nextLine;
        }
        
        return null;
    }
    
    /**
     * 清理值内容
     */
    private static String cleanValue(String value) {
        if (value == null) return "";
        
        // 去除引用标记 [1], [2] 等
        value = value.replaceAll("\\[\\d+\\]", "");
        
        // 去除多余的空格
        value = value.replaceAll("\\s+", " ").trim();
        
        // 去除开头的特殊字符
        value = value.replaceFirst("^[：:\\-—]+", "").trim();
        
        return value;
    }
    
    /**
     * 提取文本简介
     */
    private static String extractTextSummary(String text) {
        // 过滤掉无用的提示信息
        String[] skipPrefixes = {
            "本条目介绍的是", "萌娘百科欢迎您", "欢迎正在阅读",
            "此页面中存在", "提示", "注意", "关于", "请参见",
            "编辑前请阅读", "参与编辑", "警告", "游戏数据或信息受",
            "中国大陆", "台湾", "韩国", "日本", "北美", "欧洲"
        };
        
        String[] skipContains = {
            "萌娘百科祝", "度过愉快的时光", "☆Kira~",
            "协助 编辑", "查找相关资料", "条目编辑规范",
            "Wiki入门", "请注意：", "版权归", "Special:", "index.php",
            "<img", "srcset", "style=", "[编辑", "编辑源代码"
        };
        
        // 提取目录之前的内容
        int tocIndex = text.indexOf("目录");
        String intro = tocIndex > 0 ? text.substring(0, tocIndex).trim() : text;
        
        // 分段处理
        String[] lines = intro.split("\n");
        StringBuilder summary = new StringBuilder();
        int validLines = 0;
        boolean foundMainDescription = false;
        
        for (String line : lines) {
            line = line.trim();
            
            // 跳过空行
            if (line.isEmpty() || validLines >= 5) continue;
            
            // 跳过特定前缀
            boolean skip = false;
            for (String prefix : skipPrefixes) {
                if (line.startsWith(prefix)) {
                    skip = true;
                    break;
                }
            }
            
            // 跳过包含特定文本
            if (!skip) {
                for (String contains : skipContains) {
                    if (line.contains(contains)) {
                        skip = true;
                        break;
                    }
                }
            }
            
            // 跳过太短的行
            if (!skip && line.length() < 20) {
                skip = true;
            }
            
            // 跳过看起来像键值对的行（但允许更长的描述性文本）
            if (!skip && line.matches("^[^\\s]{1,8}\\s+[^\\s]+$")) {
                skip = true;
            }
            
            // 优先查找包含作品名称或描述性关键词的段落
            boolean isMainDescription = line.contains("是一款") || line.contains("是一部") || 
                                       line.contains("讲述") || line.contains("故事") ||
                                       line.contains("描述") || line.contains("以");
            
            // 添加有效行
            if (!skip && line.length() >= 20) {
                if (isMainDescription) {
                    // 优先添加主要描述
                    summary.insert(0, line + "\n");
                    foundMainDescription = true;
                    validLines++;
                } else if (validLines < 3 || !foundMainDescription) {
                    summary.append(line).append("\n");
                    validLines++;
                }
            }
        }
        
        String result = summary.toString().trim();
        
        // 限制长度
        return result.length() > 500 ? result.substring(0, 500) + "..." : result;
    }
    
    /**
     * 提取信息框
     */
    private static String extractInfobox(Element table) {
        StringBuilder sb = new StringBuilder();
        Elements rows = table.select("tr");
        
        // 扩展的关键字段
        String[] relevantKeys = {
            // 角色信息
            "中文名", "日文名", "英文名", "别名", "罗马音", "本名",
            "cv", "配音", "声优", "演员",
            "性别", "年龄", "生日", "星座", "血型",
            "身高", "体重", "三围",
            "发色", "瞳色", "肤色",
            "萌点", "特征", "职业", "所属", "出身", "居住地",
            "出场作品", "登场作品",
            // 作品信息
            "类型", "原作", "作者", "编剧", "导演", "制作人",
            "平台", "开发", "发行", "引擎", "模式",
            "发售日", "发行日期", "首播", "连载",
            "音乐", "美术", "总监", "制作",
            "集数", "话数", "状态"
        };
        
        // 需要跳过的关键词
        String[] skipKeys = {
            "相关图片", "登场集数", "使用道具", "参考资料", "注释"
        };
        
        int count = 0;
        
        for (Element row : rows) {
            if (count >= 25) break;  // 增加提取数量上限
            
            Elements ths = row.select("th");
            Elements tds = row.select("td");
            
            if (ths.isEmpty() || tds.isEmpty()) continue;
            
            String key = cleanText(ths.first()).replaceAll("[:：\\s]+$", "");
            String value = cleanText(tds.first());
            
            if (key.isEmpty() || value.isEmpty()) continue;
            
            // 跳过不需要的字段
            boolean shouldSkip = false;
            for (String skip : skipKeys) {
                if (key.contains(skip)) {
                    shouldSkip = true;
                    break;
                }
            }
            if (shouldSkip) continue;
            
            // 值的长度限制（放宽一些）
            if (value.length() > 200) {
                value = value.substring(0, 200) + "...";
            }
            
            // 检查是否为相关字段
            boolean isRelevant = false;
            for (String relevantKey : relevantKeys) {
                if (key.toLowerCase().contains(relevantKey.toLowerCase()) ||
                    relevantKey.toLowerCase().contains(key.toLowerCase())) {
                    isRelevant = true;
                    break;
                }
            }
            
            // 只添加相关字段
            if (isRelevant) {
                sb.append(key).append("：").append(value).append("\n");
                count++;
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 提取摘要（优化版）
     */
    private static String extractSummary(Document doc) {
        // 移除编辑提示框和不需要的元素
        doc.select(".editnotice, .mw-editnotice, .notice, .hatnote, " +
                   ".dablink, .catlinks, .mw-warning, .editoptions, " +
                   ".toc, #toc").remove();
        
        Elements paragraphs = doc.select("p");
        StringBuilder summary = new StringBuilder();
        int validParaCount = 0;
        
        for (Element p : paragraphs) {
            String text = cleanText(p);
            
            // 跳过太短的段落
            if (text.length() < 25) continue;
            
            // 跳过编辑提示
            if (isEditNotice(text)) continue;
            
            // 跳过只包含标点符号的段落
            if (text.matches("^[\\s\\p{P}]*$")) continue;
            
            // 添加有效段落（段落之间用换行分隔）
            summary.append(text).append("\n");
            validParaCount++;
            
            // 最多取前2-3个有效段落，控制总长度
            if (validParaCount >= 2 || summary.length() > 350) {
                break;
            }
        }
        
        String result = summary.toString().trim();
        
        // 限制总长度
        return result.length() > 400 ? result.substring(0, 400) + "..." : result;
    }
    
    /**
     * 判断是否为编辑提示文本
     */
    private static boolean isEditNotice(String text) {
        String[] noticeKeywords = {
            "萌娘百科欢迎",
            "欢迎正在阅读",
            "萌娘百科祝",
            "度过愉快的时光",
            "Kira~",
            "参与完善本条目",
            "协助 编辑",
            "编辑本条目",
            "查找相关资料",
            "Wiki入门",
            "条目编辑规范",
            "本条目介绍的是",
            "关于其他",
            "请参见",
            "此页面中存在",
            "请注意：",
            "不要添加",
            "版权归",
            "未经允许",
            "游戏数据或信息受",
            "您可能想要",
            "消歧义",
            "重定向自"
        };
        
        for (String keyword : noticeKeywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
}
