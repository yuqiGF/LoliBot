package com.bot.utils.crawler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bot.utils.common.HttpClientPool;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PRTS Wiki 爬虫。
 *
 * <p>本类负责把群聊里的 PRTS 查询语句转换为结构化卡片数据。实现上优先走 MediaWiki API，
 * 再解析移动端页面 HTML；图片不再粗暴取页面第一张，而是按「干员立绘、敌人模型、道具图标、
 * 关卡图标、公招头像」的类型规则取图，避免把技能图标误当作角色图。</p>
 */
public class PRTSCrawler {

    private static final Logger logger = LoggerFactory.getLogger(PRTSCrawler.class);

    private static final String DEFAULT_BASE_URL = "https://m.prts.wiki";
    private static final int MAX_ROWS = 42;
    private static final int MAX_RELATED = 520;
    private static final int MAX_VALUE_LENGTH = 360;
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("(?:第\\s*)?([0-9]{1,2}|[一二三四五六七八九十]{1,3})\\s*章");
    private static final Pattern STAGE_CODE_PATTERN = Pattern.compile("(?i)^[a-z]{0,4}-?\\d{1,2}-\\d{1,2}$|^[a-z]{1,5}-[a-z0-9-]+$|^\\d{1,2}-\\d{1,2}$");
    private static final List<String> RECRUIT_TAGS = List.of(
            "高级资深干员", "资深干员", "新手", "近战位", "远程位", "先锋", "近卫", "重装", "狙击", "术师",
            "医疗", "辅助", "特种", "治疗", "支援", "输出", "群攻", "减速", "生存", "防护", "削弱", "位移",
            "控场", "爆发", "召唤", "快速复活", "费用回复", "支援机械");

    private PRTSCrawler() {
    }

    public static PRTSData getInfo(String rawQuery) {
        return getInfo(rawQuery, DEFAULT_BASE_URL);
    }

    public static PRTSData getInfo(String rawQuery, String baseUrl) {
        ParsedQuery query = ParsedQuery.parse(rawQuery);
        if (query == null) {
            return null;
        }

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if (query.mode() == QueryMode.RECRUIT) {
            return getRecruitInfo(query, normalizedBaseUrl);
        }

        for (String candidate : collectCandidates(normalizedBaseUrl, query)) {
            PageHtml page = fetchPage(normalizedBaseUrl, candidate);
            if (page == null || page.html() == null || page.html().isBlank()) {
                continue;
            }

            PRTSData data = parsePage(page.html(), query, candidate, normalizedBaseUrl);
            if (data != null && data.hasContent()) {
                return data;
            }
        }
        return null;
    }

    /**
     * 收集候选页面。关卡列表、肉鸽等泛查询会先尝试语义页，再走 opensearch 兜底。
     */
    private static List<String> collectCandidates(String baseUrl, ParsedQuery query) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String term : query.semanticSearchTerms()) {
            if (term != null && !term.isBlank()) {
                candidates.add(term.trim());
                candidates.addAll(searchCandidates(baseUrl, term.trim()));
            }
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            candidates.add(query.keyword());
        }
        if (candidates.isEmpty()) {
            candidates.add(query.mode().defaultPage);
        }
        return new ArrayList<>(candidates);
    }

    /**
     * 公招计算从 PRTS 的「公开招募」页面解析头像块，根据用户输入的 tag 做包含筛选。
     */
    private static PRTSData getRecruitInfo(ParsedQuery query, String baseUrl) {
        PageHtml page = fetchPage(baseUrl, "公开招募");
        if (page == null) {
            return null;
        }

        Document doc = Jsoup.parse(page.html(), baseUrl + "/");
        cleanupDocument(doc);

        PRTSData data = baseData(query, "公开招募", "公开招募", baseUrl);
        data.cardType = CardType.RECRUIT;
        data.summary = "按标签筛选公开招募可匹配干员。选择稀有标签时请记得设置 9 小时。";

        List<String> tags = query.tags();
        data.rows.add(new PRTSRow("筛选标签", tags.isEmpty() ? "未输入标签" : String.join(" / ", tags)));
        if (tags.isEmpty()) {
            data.relatedTitles.addAll(List.of("示例：prts 高级资深干员 近卫", "示例：prts 资深干员 输出", "示例：prts 快速复活"));
            return data;
        }

        Element section = recruitSection(doc, tags);
        Map<String, PRTSImage> matched = new LinkedHashMap<>();
        for (Element icon : section.select("img#charicon, img[src*=头像_]")) {
            Element link = icon.closest("a[title]");
            Element cell = icon.closest("td");
            String name = link == null ? "" : cleanText(link.attr("title"));
            String tagText = recruitTagTextNear(cell);
            if (name.isBlank() || cell == null || matched.containsKey(name) || !containsAllTags(tagText, tags)) {
                continue;
            }
            String imageUrl = normalizeImageUrl(icon.attr("src"), baseUrl);
            File local = downloadImage(imageUrl, baseUrl);
            if (local != null) {
                matched.put(name, new PRTSImage(name, local.getAbsolutePath()));
            }
        }

        data.images.addAll(matched.values());
        data.rows.add(new PRTSRow("匹配数量", String.valueOf(data.images.size())));
        if (data.images.isEmpty()) {
            data.summary = "没有找到完全匹配的公开招募组合，可能是标签组合不存在，或 PRTS 页面结构临时变化。";
        }
        return data;
    }

    private static Element recruitSection(Document doc, List<String> tags) {
        String sectionId;
        if (tags.contains("高级资深干员")) {
            sectionId = "tabber-高级资深干员";
        } else if (tags.contains("资深干员")) {
            sectionId = "tabber-资深干员";
        } else {
            sectionId = tags.size() >= 2 ? "tabber-双TAG" : "tabber-单TAG";
        }
        Element section = doc.getElementById(sectionId);
        return section == null ? contentRoot(doc) : section;
    }

    private static String recruitTagTextNear(Element cell) {
        if (cell == null) {
            return "";
        }
        String right = recruitSiblingText(cell, true);
        if (hasKnownRecruitTag(right)) {
            return right;
        }
        String left = recruitSiblingText(cell, false);
        return hasKnownRecruitTag(left) ? left : right;
    }

    private static String recruitSiblingText(Element cell, boolean forward) {
        StringBuilder text = new StringBuilder();
        Element cursor = forward ? cell.nextElementSibling() : cell.previousElementSibling();
        for (int i = 0; i < 2 && cursor != null; i++) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(cleanText(cursor.text()));
            cursor = forward ? cursor.nextElementSibling() : cursor.previousElementSibling();
        }
        return text.toString().replace('，', ' ');
    }

    private static boolean hasKnownRecruitTag(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String tag : RECRUIT_TAGS) {
            if (text.contains(tag)) {
                return true;
            }
        }
        return false;
    }
    private static boolean containsAllTags(String rowText, List<String> tags) {
        for (String tag : tags) {
            if ("高级资深干员".equals(tag) || "资深干员".equals(tag)) {
                continue;
            }
            if (!rowText.contains(tag)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> searchCandidates(String baseUrl, String keyword) {
        List<String> result = new ArrayList<>();
        String apiUrl = baseUrl + "/api.php?action=opensearch&format=json&limit=8&search=" + encode(keyword);

        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpGet get = new HttpGet(apiUrl);
            applyPrtsHeaders(get, baseUrl);

            try (CloseableHttpResponse response = client.execute(get)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    return result;
                }
                JSONArray json = JSONArray.parseArray(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
                JSONArray titles = json == null || json.size() < 2 ? null : json.getJSONArray(1);
                for (int i = 0; titles != null && i < titles.size(); i++) {
                    String title = titles.getString(i);
                    if (title != null && !title.isBlank()) {
                        result.add(title);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("PRTS 搜索异常: {}", e.getMessage());
        }
        return result;
    }

    private static PageHtml fetchPage(String baseUrl, String pageTitle) {
        PageHtml viaApi = fetchViaParseApi(baseUrl, pageTitle);
        return viaApi != null ? viaApi : fetchViaWikiPage(baseUrl, pageTitle);
    }

    private static PageHtml fetchViaParseApi(String baseUrl, String pageTitle) {
        String apiUrl = baseUrl + "/api.php?action=parse&format=json&redirects=1&prop=text|displaytitle&page=" + encode(pageTitle);
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpGet get = new HttpGet(apiUrl);
            applyPrtsHeaders(get, baseUrl);
            try (CloseableHttpResponse response = client.execute(get)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    return null;
                }
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (body == null || body.startsWith("<")) {
                    return null;
                }
                JSONObject parse = JSONObject.parseObject(body).getJSONObject("parse");
                if (parse == null) {
                    return null;
                }
                JSONObject text = parse.getJSONObject("text");
                String html = text == null ? null : text.getString("*");
                String title = parse.getString("displaytitle");
                return html == null || html.isBlank() ? null : new PageHtml(title == null ? pageTitle : cleanText(title), html);
            }
        } catch (Exception e) {
            logger.debug("PRTS parse API 异常: {}", e.getMessage());
            return null;
        }
    }

    private static PageHtml fetchViaWikiPage(String baseUrl, String pageTitle) {
        String pageUrl = baseUrl + "/w/" + encode(pageTitle).replace("+", "%20");
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpGet get = new HttpGet(pageUrl);
            applyPrtsHeaders(get, baseUrl);
            try (CloseableHttpResponse response = client.execute(get)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    return null;
                }
                String html = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(html);
                String title = cleanText(textOf(doc.selectFirst("#firstHeading")));
                if (title.contains("没有这个页面") || title.contains("创建本页面")) {
                    return null;
                }
                return new PageHtml(title.isBlank() ? pageTitle : title, html);
            }
        } catch (Exception e) {
            logger.debug("PRTS 页面访问异常: {}", e.getMessage());
            return null;
        }
    }

    private static PRTSData parsePage(String html, ParsedQuery query, String fallbackTitle, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl + "/");
        Map<String, String> rawCharInfo = parseCharInfo(html);
        cleanupDocument(doc);
        String pageTitle = firstNonBlank(cleanText(textOf(doc.selectFirst("#firstHeading"))), cleanText(doc.title()), fallbackTitle);
        if (pageTitle.contains("没有这个页面") || pageTitle.contains("Permission error")) {
            return null;
        }

        QueryMode actualMode = query.mode() == QueryMode.GENERAL ? detectMode(doc, pageTitle) : query.mode();
        PRTSData data = baseData(query, pageTitle, fallbackTitle, baseUrl);
        data.modeKeyword = actualMode.primaryAlias();
        data.modeName = actualMode.displayName;
        data.listOverview = query.prefersListOverview(actualMode);

        if (actualMode == QueryMode.OPERATOR) {
            data.cardType = CardType.OPERATOR;
            data.operatorOverview = true;
            extractOperator(doc, data, baseUrl, rawCharInfo);
        } else if (actualMode == QueryMode.ENEMY) {
            data.cardType = CardType.ENEMY;
            extractEnemy(doc, data, baseUrl);
        } else {
            extractGeneric(doc, query, actualMode, data, baseUrl);
        }

        trimData(data);
        return data;
    }

    private static PRTSData baseData(ParsedQuery query, String pageTitle, String fallbackTitle, String baseUrl) {
        PRTSData data = new PRTSData();
        data.query = query.raw();
        data.keyword = query.keyword();
        data.modeKeyword = query.mode().primaryAlias();
        data.modeName = query.mode().displayName;
        data.pageTitle = pageTitle;
        data.sourceUrl = baseUrl + "/w/" + encode(fallbackTitle).replace("+", "%20");
        return data;
    }

    private static boolean looksLikeOperatorPage(Document doc) {
        return doc.selectFirst(".charinfo-container, table.char-base-attr-table") != null || doc.html().contains("var char_info=");
    }

    /**
     * 用户不再输入二级指令，因此这里根据真实页面结构自动判断类型。
     */
    private static QueryMode detectMode(Document doc, String pageTitle) {
        if (looksLikeOperatorPage(doc)) {
            return QueryMode.OPERATOR;
        }
        if (doc.selectFirst("table.enemy-info-level, .enemy-info-common") != null || doc.html().contains("enemy_spine")) {
            return QueryMode.ENEMY;
        }
        String title = pageTitle == null ? "" : pageTitle;
        if (STAGE_CODE_PATTERN.matcher(title.split(" ")[0]).matches()
                || doc.selectFirst("a[title=关卡一览]") != null
                || doc.html().contains("模板:普通关卡信息")
                || (doc.text().contains("推荐等级") && doc.text().contains("首次掉落"))) {
            return QueryMode.STAGE;
        }
        if (doc.selectFirst("img[src*=item_icon], img[src*=道具_]") != null
                || doc.html().contains("data-item=")
                || doc.selectFirst("a[title=道具一览]") != null) {
            return QueryMode.ITEM;
        }
        if (doc.selectFirst("a[title=时装回廊]") != null || title.contains("时装") || doc.text().contains("时装")) {
            return QueryMode.SKIN;
        }
        return QueryMode.OPERATOR;
    }

    private static void extractOperator(Document doc, PRTSData data, String baseUrl, Map<String, String> rawCharInfo) {
        Map<String, String> info = rawCharInfo == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawCharInfo);
        if (info.isEmpty()) {
            info.putAll(parseCharInfo(doc.html()));
        }
        addRow(data, "英文名", info.get("nameEn"));
        addRow(data, "星级", starText(info.get("star")));
        addRow(data, "职业", joinNonBlank(info.get("class"), info.get("branch")));
        addRow(data, "位置/标签", joinNonBlank(info.get("pos"), info.get("tag")));
        addRow(data, "阵营", info.get("group"));
        addRow(data, "画师", info.get("painter"));
        extractOperatorStats(doc, data);
        extractOperatorTalents(doc, data);
        extractOperatorSkills(doc, data, baseUrl);
        extractSummary(doc, data);

        String pageName = data.pageTitle.replaceAll("\\s+-\\s+PRTS.*$", "");
        String name = cleanText(firstNonBlank(info.get("name"), pageName));
        if (name.isBlank() || name.length() > 16 || name.contains("PRTS") || name.contains("玩家") || name.contains("搜索")) {
            name = pageName;
        }
        String artUrl = firstExistingImageInfo(baseUrl,
                "文件:半身像_" + name + "_1.png",
                "文件:半身像_" + name + "_2.png",
                "文件:立绘_" + name + "_1.png",
                "文件:立绘_" + name + "_2.png",
                "文件:立绘_" + name + ".png");
        setMainImage(data, artUrl, baseUrl);
        if (data.imagePath == null) {
            extractTypedImage(doc, data, baseUrl, QueryMode.OPERATOR);
        }
    }

    private static Map<String, String> parseCharInfo(String html) {
        Map<String, String> info = new LinkedHashMap<>();
        String source = html == null ? "" : html;
        Matcher blockMatcher = Pattern.compile("var\\s+char_info\\s*=\\s*\\{(.*?)\\n\\s*},?\\s*\\n\\s*var", Pattern.DOTALL).matcher(source);
        boolean found = blockMatcher.find();
        if (!found) {
            blockMatcher = Pattern.compile("var\\s+char_info\\s*=\\s*\\{(.*?)\\n\\s*},?", Pattern.DOTALL).matcher(source);
            found = blockMatcher.find();
        }
        String block = found ? blockMatcher.group(1) : source;
        for (String key : List.of("name", "nameEn", "star", "group", "class", "branch", "pos", "tag", "painter")) {
            Matcher kv = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([0-9]+))").matcher(block);
            if (kv.find()) {
                info.put(key, firstNonBlank(kv.group(1), kv.group(2)));
            }
        }
        return info;
    }

    private static void extractOperatorStats(Document doc, PRTSData data) {
        for (Element row : doc.select("table.char-base-attr-table tr")) {
            String label = cleanText(textOf(row.selectFirst("th")));
            if (label.isBlank() || label.contains("精英") || label.contains("属性") || label.length() > 20) {
                continue;
            }
            String value = operatorMaxLevelCellText(row);
            if (!value.isBlank()) {
                addRow(data, normalizeStatLabel(label), value);
            }
        }
        for (Element table : doc.select("table.wikitable.logo")) {
            String tableText = cleanText(table.text());
            if (!tableText.contains("初始部署费用") && !tableText.contains("再部署时间")) {
                continue;
            }
            Elements cells = table.select("th,td");
            for (int i = 0; i + 1 < cells.size(); i += 2) {
                String label = cleanText(cells.get(i).text());
                String value = cleanText(cells.get(i + 1).text());
                if (List.of("初始部署费用", "再部署时间", "阻挡数", "攻击间隔", "攻击速度", "部署费用").contains(label)) {
                    addRow(data, normalizeStatLabel(label), value);
                }
            }
            break;
        }
    }

    private static void extractOperatorTalents(Document doc, PRTSData data) {
        Element section = findSectionByHeading(doc, "天赋");
        if (section == null) {
            return;
        }
        long added = 0;
        for (Element table : section.select("table.wikitable")) {
            String talentName = "天赋";
            for (Element row : table.select("tr")) {
                Elements cells = row.children();
                if (cells.size() >= 3 && cleanText(cells.get(0).text()).contains("天赋")) {
                    continue;
                }
                if (cells.size() < 3) {
                    continue;
                }
                String nameFromRow = cleanText(cells.get(0).text());
                String condition = cleanText(cells.get(1).text());
                String description = cleanText(cells.last().text());
                if (!nameFromRow.isBlank()) {
                    talentName = nameFromRow;
                }
                if (condition.contains("条件") || description.contains("描述") || description.length() < 4) {
                    continue;
                }
                data.skills.add(new PRTSSkill(talentName.isBlank() ? "天赋" : talentName,
                        "天赋 · " + abbreviate(condition, 22), abbreviate(description, 170), null));
                added++;
                if (added >= 4) {
                    return;
                }
            }
        }
    }

    private static Element firstDecodedImage(Element root, String keyword) {
        if (root == null || keyword == null || keyword.isBlank()) {
            return null;
        }
        for (Element image : root.select("img[src]")) {
            String src = normalizeImageUrl(image.attr("src"), DEFAULT_BASE_URL);
            String alt = cleanText(image.attr("alt"));
            String haystack = src + " " + decodeForMatch(src) + " " + alt;
            if (haystack.contains(keyword)) {
                return image;
            }
        }
        return null;
    }

    private static String joinCellTexts(Elements cells) {
        StringBuilder joined = new StringBuilder();
        for (Element cell : cells) {
            String text = cleanText(cell.text());
            if (text.isBlank()) {
                List<String> titles = new ArrayList<>();
                for (Element link : cell.select("a[title]")) {
                    String title = cleanText(link.attr("title"));
                    if (!title.isBlank() && !titles.contains(title)) {
                        titles.add(title);
                    }
                }
                text = String.join(" ", titles);
            }
            if (text.isBlank()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(text);
        }
        return joined.toString();
    }

    private static String operatorMaxLevelCellText(Element row) {
        Element table = row.closest("table");
        Elements rowCells = row.children();
        if (table != null) {
            Element header = table.selectFirst("tr");
            Elements headerCells = header == null ? new Elements() : header.children();
            for (int i = headerCells.size() - 1; i >= 1; i--) {
                String headerText = cleanText(headerCells.get(i).text());
                if (headerText.contains("信赖") || headerText.contains("潜能")) {
                    continue;
                }
                if ((headerText.contains("满级") || headerText.contains("1级")) && rowCells.size() > i) {
                    String value = cleanText(rowCells.get(i).text());
                    if (!value.isBlank() && !value.equals("——")) {
                        return abbreviate(value, 32);
                    }
                }
            }
        }
        Elements cells = row.select("td");
        if (cells.size() >= 2) {
            for (int i = cells.size() - 2; i >= 0; i--) {
                String value = cleanText(cells.get(i).text());
                if (!value.isBlank() && !value.equals("——")) {
                    return abbreviate(value, 32);
                }
            }
        }
        return lastUsefulCellText(cells);
    }
    private static String lastUsefulCellText(Elements cells) {
        for (int i = cells.size() - 1; i >= 0; i--) {
            String text = cleanText(cells.get(i).text());
            if (!text.isBlank() && !text.equals("——")) {
                return abbreviate(text, 32);
            }
        }
        return "";
    }

    private static void extractOperatorSkills(Document doc, PRTSData data, String baseUrl) {
        Element section = findSectionByHeading(doc, "技能");
        if (section == null) {
            return;
        }
        String currentOpen = "";
        int skillCount = 0;
        for (Element child : section.children()) {
            if (child.tagName().equals("p") && child.text().contains("技能")) {
                currentOpen = cleanText(child.text());
            }
            if (!child.tagName().equals("table")) {
                continue;
            }
            Element img = firstDecodedImage(child, "技能_");
            String tableName = cleanText(textOf(child.selectFirst("big")));
            String altName = img == null ? "" : cleanText(img.attr("alt")).replace("技能_", "");
            String name = firstNonBlank(tableName, altName, "技能" + (skillCount + 1));
            String desc = bestSkillDescription(child);
            if (desc.isBlank() || name.contains("专精") || name.contains("材料")) {
                continue;
            }
            String imagePath = null;
            if (img != null) {
                File local = downloadImage(normalizeImageUrl(img.attr("src"), baseUrl), baseUrl);
                imagePath = local == null ? null : local.getAbsolutePath();
            }
            data.skills.add(new PRTSSkill(name, abbreviate(firstNonBlank(currentOpen, "技能"), 24), abbreviate(desc, 190), imagePath));
            skillCount++;
            if (skillCount >= 3) {
                return;
            }
        }
    }

    private static String bestSkillDescription(Element table) {
        String best = "";
        for (Element cell : table.select("td")) {
            String text = cleanText(cell.text());
            if (text.length() > best.length() && text.length() < 260 && !text.contains("等级 初始 消耗")) {
                best = text;
            }
        }
        return best;
    }

    private static void extractEnemy(Document doc, PRTSData data, String baseUrl) {
        extractEnemySummary(doc, data);
        int levelIndex = 0;
        for (Element levelTable : doc.select("table.enemy-info-level")) {
            Map<String, String> stats = new LinkedHashMap<>();
            for (Element value : levelTable.select(".statusValue")) {
                String key = switch (value.attr("data-status")) {
                    case "hp" -> "生命";
                    case "atk" -> "攻击";
                    case "def" -> "防御";
                    case "res" -> "法抗";
                    case "movespeed" -> "移速";
                    case "atkspeed" -> "攻速";
                    case "eres" -> "元素抗";
                    case "epres" -> "损伤抵抗";
                    default -> "";
                };
                if (!key.isBlank() && !stats.containsKey(key)) {
                    stats.put(key, cleanText(value.text()));
                }
            }
            String prefix = "级别" + levelIndex + " ";
            stats.forEach((k, v) -> addRow(data, prefix + k, v));
            String ability = extractEnemyAbility(levelTable);
            if (!ability.isBlank()) {
                data.skills.add(new PRTSSkill("能力", "级别" + levelIndex, abbreviate(ability, 220), null));
            }
            levelIndex++;
        }
        if (data.summary == null || data.summary.isBlank()) {
            extractSummary(doc, data);
        }
        String enemyFile = extractEnemyFile(doc.html());
        if (!enemyFile.isBlank()) {
            setMainImage(data, "https://torappu.prts.wiki/assets/enemy_spine/" + enemyFile + "/" + enemyFile + ".png", baseUrl);
            if (data.imagePath == null) {
                setMainImage(data, "https://torappu.prts.wiki/assets/enemy_icon/" + enemyFile + ".png", baseUrl);
            }
        }
        if (data.imagePath == null) {
            Element icon = doc.selectFirst("table.enemy-info-common img[src]");
            if (icon != null) {
                setMainImage(data, normalizeImageUrl(icon.attr("src"), baseUrl), baseUrl);
            }
        }
        if (data.imagePath == null) {
            extractTypedImage(doc, data, baseUrl, QueryMode.ENEMY);
        }
    }

    private static void extractEnemySummary(Document doc, PRTSData data) {
        Element common = doc.selectFirst("table.enemy-info-common");
        String description = cleanText(textOf(common == null ? null : common.selectFirst("p")));
        if (description.isBlank()) {
            for (Element table : doc.select("table.enemy-info-level")) {
                description = cleanText(textOf(table.selectFirst("p")));
                if (!description.isBlank()) {
                    break;
                }
            }
        }
        if (!description.isBlank()) {
            data.summary = abbreviate(description, 360);
        }
    }

    private static String extractEnemyAbility(Element levelTable) {
        for (Element row : levelTable.select("tr")) {
            String label = cleanText(textOf(row.selectFirst("th")));
            if (!label.contains("能力") && !label.contains("特殊")) {
                continue;
            }
            String value = joinCellTexts(row.select("td"));
            if (!value.isBlank() && !value.equals("—") && !value.equals("-")) {
                return value;
            }
        }
        return "";
    }

    private static String extractEnemyFile(String html) {
        Matcher matcher = Pattern.compile("\\\"file\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static void extractGeneric(Document doc, ParsedQuery query, QueryMode mode, PRTSData data, String baseUrl) {
        if (mode == QueryMode.SKIN && extractNamedSkin(doc, query, data, baseUrl)) {
            return;
        }
        extractSummary(doc, data);
        extractModeSections(doc, mode, data);
        extractInfoRows(doc, data);
        if (mode == QueryMode.ITEM) {
            extractItemDetails(doc, data);
            syncSummaryFromRows(data, "用途", "描述");
        }
        if (mode == QueryMode.STAGE) {
            extractStageEnemies(doc, data, baseUrl);
        }
        extractRelatedTitles(doc, mode, data);
        polishListOverview(query, mode, data);
        extractTypedImage(doc, data, baseUrl, mode);
    }

    private static boolean extractNamedSkin(Document doc, ParsedQuery query, PRTSData data, String baseUrl) {
        String keyword = query.keyword() == null ? "" : query.keyword().trim();
        if (keyword.isBlank() || keyword.equals(data.pageTitle)) {
            return false;
        }

        Element detail = findSectionByHeading(doc, keyword);
        Element card = null;
        for (Element container : doc.select(".charskinbtn-container")) {
            if (cleanText(container.text()).contains(keyword)) {
                card = container;
                break;
            }
        }
        if (detail == null && card == null) {
            return false;
        }

        data.pageTitle = keyword;
        data.summary = "已从 PRTS 时装回廊定位到指定时装。";
        if (card != null) {
            Element operator = card.selectFirst("a[title]");
            if (operator != null && !cleanText(operator.attr("title")).isBlank()) {
                addRow(data, "所属干员", cleanText(operator.attr("title")));
            }
            Element image = card.selectFirst(".charimg img[src]");
            if (image != null) {
                setMainImage(data, normalizeImageUrl(image.attr("src"), baseUrl), baseUrl);
            }
        }
        if (detail != null) {
            String detailSummary = cleanText(detail.select("td p").text());
            if (!detailSummary.isBlank()) {
                data.summary = abbreviate(detailSummary, 260);
            }
            for (Element row : detail.select("tr")) {
                Elements cells = row.children();
                for (int i = 0; i + 1 < cells.size(); i += 2) {
                    String label = cleanText(cells.get(i).text());
                    String value = cleanText(cells.get(i + 1).text());
                    if (isUsefulRow(label, value)) {
                        addRow(data, abbreviate(label, 18), abbreviate(value, MAX_VALUE_LENGTH));
                    }
                }
            }
        }
        if (data.imagePath == null) {
            extractTypedImage(doc, data, baseUrl, QueryMode.SKIN);
        }
        return data.hasContent();
    }
    /**
     * 类型化取图：先匹配明确资源，再退回页面内同类型图片。
     */
    private static void extractTypedImage(Document doc, PRTSData data, String baseUrl, QueryMode mode) {
        String title = data.pageTitle.replaceAll("\\s+-\\s+PRTS.*$", "");
        String url = switch (mode) {
            case ITEM -> firstExistingImageInfo(baseUrl, "文件:道具_带框_" + title + ".png", "文件:道具_" + title + ".png");
            case SKIN -> firstExistingImageInfo(baseUrl, "文件:立绘_" + title + ".png", "文件:时装_" + title + ".png");
            case STAGE -> firstStageMapImage(doc, baseUrl);
            default -> "";
        };
        if (url.isBlank() && mode == QueryMode.OPERATOR) {
            url = firstMatchingDomImage(doc, baseUrl, List.of("半身像_" + title, "立绘_" + title, "头像_" + title));
        } else if (url.isBlank() && mode != QueryMode.STAGE) {
            url = firstMatchingDomImage(doc, baseUrl, List.of(title, "道具", "头像", "图标", "立绘"));
        }
        setMainImage(data, url, baseUrl);
    }

    private static String firstStageMapImage(Document doc, String baseUrl) {
        for (Element image : doc.select("img[src]")) {
            String src = normalizeImageUrl(image.attr("src"), baseUrl);
            String alt = cleanText(image.attr("alt"));
            String decoded = decodeForMatch(src);
            if ((decoded.contains("/assets/map_preview/") || alt.contains("地图")) && isUsefulImageUrl(src)) {
                return src;
            }
        }
        return "";
    }

    private static String firstExistingImageInfo(String baseUrl, String... fileTitles) {
        for (String title : fileTitles) {
            String url = queryImageInfo(baseUrl, title);
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
    }

    private static String queryImageInfo(String baseUrl, String fileTitle) {
        String apiUrl = baseUrl + "/api.php?action=query&format=json&prop=imageinfo&iiprop=url&titles=" + encode(fileTitle);
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpGet get = new HttpGet(apiUrl);
            applyPrtsHeaders(get, baseUrl);
            try (CloseableHttpResponse response = client.execute(get)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    return "";
                }
                JSONObject query = JSONObject.parseObject(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)).getJSONObject("query");
                if (query == null || query.getJSONObject("pages") == null) {
                    return "";
                }
                JSONObject pages = query.getJSONObject("pages");
                for (String key : pages.keySet()) {
                    JSONArray imageInfo = pages.getJSONObject(key).getJSONArray("imageinfo");
                    if (imageInfo != null && !imageInfo.isEmpty()) {
                        return imageInfo.getJSONObject(0).getString("url");
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("PRTS imageinfo 失败: {}", e.getMessage());
        }
        return "";
    }

    private static String firstMatchingDomImage(Document doc, String baseUrl, List<String> includes) {
        for (Element image : doc.select("img[src]")) {
            String src = normalizeImageUrl(image.attr("src"), baseUrl);
            String alt = cleanText(image.attr("alt"));
            String haystack = src + " " + decodeForMatch(src) + " " + alt;
            boolean matched = includes.isEmpty();
            for (String include : includes) {
                if (haystack.contains(include)) {
                    matched = true;
                    break;
                }
            }
            if (matched && isUsefulImageUrl(src)) {
                return src;
            }
        }
        return "";
    }

    private static void setMainImage(PRTSData data, String imageUrl, String baseUrl) {
        if (imageUrl == null || imageUrl.isBlank() || data.imagePath != null) {
            return;
        }
        File local = downloadImage(normalizeImageUrl(imageUrl, baseUrl), baseUrl);
        if (local != null) {
            data.imageUrl = imageUrl;
            data.imagePath = local.getAbsolutePath();
        }
    }

    private static void extractSummary(Document doc, PRTSData data) {
        StringBuilder summary = new StringBuilder();
        for (Element p : contentRoot(doc).select("> p, .mw-parser-output > p")) {
            String text = cleanText(p.text());
            if (text.length() < 12 || text.contains("如需了解")) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append("\n");
            }
            summary.append(text);
            if (summary.length() >= 260) {
                break;
            }
        }
        data.summary = abbreviate(summary.toString(), 360);
    }

    private static void extractModeSections(Document doc, QueryMode mode, PRTSData data) {
        for (String keyword : mode.sectionKeywords) {
            Element section = findSectionByHeading(doc, keyword);
            if (section == null) {
                continue;
            }
            String text = abbreviate(cleanText(section.text()), MAX_VALUE_LENGTH);
            if (!text.isBlank()) {
                data.rows.add(new PRTSRow(keyword, text));
            }
            if (data.rows.size() >= 4) {
                return;
            }
        }
    }

    private static Element findSectionByHeading(Document doc, String keyword) {
        for (Element heading : doc.select("h2, h3, h4")) {
            if (cleanText(heading.text()).contains(keyword)) {
                return heading.nextElementSibling();
            }
        }
        return null;
    }

    private static void extractInfoRows(Document doc, PRTSData data) {
        for (Element row : doc.select("table tr, .wikitable tr, .infobox tr")) {
            if (data.rows.size() >= MAX_ROWS) {
                return;
            }
            Elements headers = row.select("> th");
            Elements values = row.select("> td");
            if (headers.isEmpty() || values.isEmpty()) {
                continue;
            }
            String label = cleanText(headers.first().text());
            String value = cleanText(values.first().text());
            if (isUsefulRow(label, value)) {
                data.rows.add(new PRTSRow(abbreviate(label, 24), abbreviate(value, MAX_VALUE_LENGTH)));
            }
        }
    }

    private static boolean isUsefulRow(String label, String value) {
        return label != null && value != null && !label.isBlank() && !value.isBlank()
                && label.length() <= 30 && value.length() >= 2
                && !label.contains("编辑") && !value.contains("编辑") && !label.equals(value);
    }

    private static void extractItemDetails(Document doc, PRTSData data) {
        Element section = findSectionByHeading(doc, "基础信息");
        if (section == null) {
            return;
        }
        String pendingLabel = "";
        for (Element row : section.select("tr")) {
            String label = cleanText(textOf(row.selectFirst("th")));
            String value = joinCellTexts(row.select("td"));
            if (!label.isBlank()) {
                pendingLabel = label;
            }
            if (value.isBlank()) {
                continue;
            }
            String actualLabel = firstNonBlank(label, pendingLabel);
            if (isUsefulRow(actualLabel, value) && data.rows.stream().noneMatch(r -> r.label().equals(actualLabel))) {
                data.rows.add(new PRTSRow(abbreviate(actualLabel, 24), abbreviate(value, MAX_VALUE_LENGTH)));
                pendingLabel = "";
            }
        }
    }
    private static void extractStageEnemies(Document doc, PRTSData data, String baseUrl) {
        Element table = doc.selectFirst("table.stage-enemy-table");
        if (table == null) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Element row : table.select("tr")) {
            Element icon = firstDecodedImage(row, "头像_敌人");
            Element cell = icon == null ? null : icon.closest("td");
            Element link = cell == null ? null : cell.selectFirst("a[title]");
            if (icon == null || link == null) {
                continue;
            }
            String name = cleanText(link.attr("title"));
            if (name.isBlank() || !seen.add(name)) {
                continue;
            }
            File local = downloadImage(normalizeImageUrl(icon.attr("src"), baseUrl), baseUrl);
            if (local != null) {
                data.images.add(new PRTSImage(name, local.getAbsolutePath()));
            }
            if (data.images.size() >= 12) {
                return;
            }
        }
    }
    private static void syncSummaryFromRows(PRTSData data, String... labels) {
        StringBuilder summary = new StringBuilder();
        for (String label : labels) {
            for (PRTSRow row : data.rows) {
                if (row.label().equals(label) && !row.value().isBlank()) {
                    if (summary.length() > 0) {
                        summary.append("\n");
                    }
                    summary.append(row.label()).append("：").append(row.value());
                    break;
                }
            }
        }
        if (summary.length() > 0) {
            data.summary = abbreviate(summary.toString(), 360);
        }
    }

    private static void extractRelatedTitles(Document doc, QueryMode mode, PRTSData data) {
        Set<String> titles = new LinkedHashSet<>();
        for (Element link : contentRoot(doc).select("a[title]")) {
            if (titles.size() >= MAX_RELATED) {
                break;
            }
            String value = firstNonBlank(cleanText(link.text()), cleanText(link.attr("title")));
            if (isUsefulTitle(value, mode)) {
                titles.add(value);
            }
        }
        data.relatedTitles.addAll(titles);
    }

    private static boolean isUsefulTitle(String title, QueryMode mode) {
        if (title == null || title.length() < 2 || title.length() > 28) {
            return false;
        }
        if (title.contains(":") || title.contains("编辑") || title.contains("页面不存在") || title.contains("PRTS")) {
            return false;
        }
        return mode != QueryMode.STAGE || title.matches(".*([A-Z]{1,5}-[A-Z0-9-]+|[0-9]{1,2}-[0-9]{1,2}).*")
                || title.contains("行动") || title.contains("集成战略") || title.contains("探索");
    }

    private static void polishListOverview(ParsedQuery query, QueryMode mode, PRTSData data) {
        filterStageOverview(query, mode, data);
        if (!data.listOverview) {
            return;
        }
        data.summary = "已按“" + mode.displayName + "”整理候选条目。可从下方挑一个名称继续查询详情。";
        data.rows.clear();
        if (data.relatedTitles.isEmpty()) {
            data.relatedTitles.addAll(mode.fallbackItems(query.keyword()));
        }
    }

    private static void filterStageOverview(ParsedQuery query, QueryMode mode, PRTSData data) {
        if (mode != QueryMode.STAGE || query.keyword() == null) {
            return;
        }
        int chapter = toChapterNumber(query.keyword());
        if (chapter <= 0 || data.relatedTitles.isEmpty()) {
            return;
        }
        List<String> filtered = new ArrayList<>();
        Pattern stagePrefix = Pattern.compile("(?i).*(^|[^0-9A-Z])(?:H|S)?" + chapter + "-\\d+.*");
        for (String title : data.relatedTitles) {
            if (stagePrefix.matcher(title).matches()) {
                filtered.add(title);
            }
        }
        if (!filtered.isEmpty()) {
            data.relatedTitles = filtered;
        }
    }
    private static void cleanupDocument(Document doc) {
        doc.select("script:not(:contains(char_info)):not(:contains(statusRankData)), style, noscript, .mw-editsection, .reference, .references, .toc, .printfooter, .catlinks, .metadata").remove();
    }

    private static Element contentRoot(Document doc) {
        Element root = doc.selectFirst(".mw-parser-output");
        return root == null ? doc.body() : root;
    }

    private static void trimData(PRTSData data) {
        if (data.summary == null || data.summary.isBlank()) {
            data.summary = "已从 PRTS 获取条目信息。";
        }
        if (data.rows.size() > MAX_ROWS) {
            data.rows = new ArrayList<>(data.rows.subList(0, MAX_ROWS));
        }
        if (data.relatedTitles.size() > 30) {
            data.relatedTitles = new ArrayList<>(data.relatedTitles.subList(0, 30));
        }
    }

    private static File downloadImage(String imageUrl, String baseUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpGet get = new HttpGet(imageUrl);
            applyPrtsHeaders(get, baseUrl);
            try (CloseableHttpResponse response = client.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    return null;
                }
                File dir = new File(System.getProperty("java.io.tmpdir"), "lolibot_prts_images");
                if (!dir.exists() && !dir.mkdirs()) {
                    return null;
                }
                File target = new File(dir, "prts_" + safePositiveHash(imageUrl) + imageExtension(imageUrl));
                try (InputStream input = response.getEntity().getContent(); FileOutputStream output = new FileOutputStream(target)) {
                    input.transferTo(output);
                }
                return target.length() > 0 ? target : null;
            }
        } catch (Exception e) {
            logger.debug("PRTS 图片下载失败: {}", e.getMessage());
            return null;
        }
    }

    private static void applyPrtsHeaders(HttpGet get, String baseUrl) {
        get.setHeader("User-Agent", HttpClientPool.getRandomUserAgent());
        get.setHeader("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8");
        get.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
        get.setHeader("Referer", baseUrl + "/");
    }

    private static String normalizeImageUrl(String rawUrl, String baseUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String value = rawUrl.trim();
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.startsWith("/")) {
            return baseUrl + value;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return baseUrl + "/" + value;
    }

    private static String decodeForMatch(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }
    private static boolean isUsefulImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        return !lower.contains("/common") && !lower.contains("sprite") && !lower.endsWith(".svg")
                && (lower.contains("media.prts.wiki") || lower.contains("torappu.prts.wiki"));
    }

    private static String imageExtension(String imageUrl) {
        String path = URI.create(imageUrl).getPath().toLowerCase();
        for (String ext : List.of(".png", ".jpg", ".jpeg", ".webp")) {
            if (path.endsWith(ext)) {
                return ext.equals(".jpeg") ? ".jpg" : ext;
            }
        }
        return ".png";
    }

    private static void addRow(PRTSData data, String label, String value) {
        if (value != null && !value.isBlank()) {
            data.rows.add(new PRTSRow(label, abbreviate(value, 180)));
        }
    }

    private static String normalizeStatLabel(String label) {
        return switch (label) {
            case "攻击力" -> "攻击";
            case "防御力" -> "防御";
            case "法术抗性" -> "法抗";
            case "最大生命值" -> "生命";
            default -> label;
        };
    }

    private static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return Jsoup.parse(text).text().replaceAll("\\[[^\\]]*]", "").replaceAll("\\s+", " ").trim();
    }

    private static String textOf(Element element) {
        return element == null ? "" : element.text();
    }

    private static String abbreviate(String text, int maxLength) {
        String cleaned = cleanText(text);
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String joinNonBlank(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null ? "" : right;
        }
        if (right == null || right.isBlank()) {
            return left;
        }
        return left + " / " + right;
    }

    private static String starText(String star) {
        if (star == null || star.isBlank()) {
            return "";
        }
        try {
            return String.valueOf(Integer.parseInt(star) + 1) + "★";
        } catch (NumberFormatException e) {
            return star;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static int safePositiveHash(String value) {
        return (value == null ? 0 : value.hashCode()) & 0x7fffffff;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = firstNonBlank(baseUrl, DEFAULT_BASE_URL);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static int toChapterNumber(String value) {
        if (value == null) {
            return -1;
        }
        Matcher matcher = CHAPTER_PATTERN.matcher(value);
        String token = matcher.find() ? matcher.group(1) : value.trim();
        if (token.matches("\\d{1,2}")) {
            return Integer.parseInt(token);
        }
        List<String> chinese = List.of("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十");
        return chinese.indexOf(token);
    }
    private static String toChineseChapter(String value) {
        String number = value == null ? "" : value.trim();
        if (number.matches("[一二三四五六七八九十]{1,3}")) {
            return number;
        }
        try {
            String[] chinese = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十"};
            int parsed = Integer.parseInt(number);
            return parsed >= 0 && parsed < chinese.length ? chinese[parsed] : number;
        } catch (NumberFormatException e) {
            return number;
        }
    }

    private record PageHtml(String title, String html) {
    }

    private record ParsedQuery(String raw, QueryMode mode, String keyword) {
        private static ParsedQuery parse(String rawQuery) {
            String raw = rawQuery == null ? "" : rawQuery.trim();
            if (raw.isBlank()) {
                return null;
            }
            if (looksLikeRecruitTags(raw)) {
                return new ParsedQuery(raw, QueryMode.RECRUIT, raw);
            }
            if (looksLikeStageQuery(raw)) {
                return new ParsedQuery(raw, QueryMode.STAGE, raw);
            }
            if (looksLikeSkinQuery(raw)) {
                return new ParsedQuery(raw, QueryMode.SKIN, raw);
            }
            return new ParsedQuery(raw, QueryMode.GENERAL, raw);
        }

        private static boolean looksLikeRecruitTags(String raw) {
            String normalized = raw.replace('，', ' ').replace(',', ' ');
            int count = 0;
            for (String tag : RECRUIT_TAGS) {
                if (normalized.contains(tag)) {
                    count++;
                }
            }
            return count >= 1 && raw.length() <= 40;
        }

        private static boolean looksLikeSkinQuery(String raw) {
            return raw.contains("时装") || raw.contains("皮肤") || raw.matches(".*[A-Za-z]{1,6}\\d{2,4}.*");
        }
        private static boolean looksLikeStageQuery(String raw) {
            return STAGE_CODE_PATTERN.matcher(raw).matches()
                    || raw.contains("主线") || raw.contains("章节") || raw.contains("肉鸽")
                    || raw.contains("集成战略") || CHAPTER_PATTERN.matcher(raw).find();
        }

        private List<String> tags() {
            if (keyword == null || keyword.isBlank()) {
                return List.of();
            }
            return List.of(keyword.trim().split("\\s+"));
        }

        private List<String> semanticSearchTerms() {
            LinkedHashSet<String> terms = new LinkedHashSet<>(mode.semanticTerms(keyword == null ? "" : keyword.trim()));
            if (keyword != null && !keyword.isBlank()) {
                terms.add(keyword.trim());
            }
            if (terms.isEmpty()) {
                terms.add(mode.defaultPage);
            }
            return new ArrayList<>(terms);
        }

        private boolean prefersListOverview(QueryMode detectedMode) {
            String value = keyword == null ? "" : keyword.trim();
            if (value.isBlank()) {
                return detectedMode != QueryMode.GENERAL && detectedMode != QueryMode.OPERATOR && detectedMode != QueryMode.ENEMY;
            }
            if (detectedMode == QueryMode.STAGE && STAGE_CODE_PATTERN.matcher(value).matches()) {
                return false;
            }
            String lower = value.toLowerCase();
            return lower.contains("list") || value.contains("一览") || value.contains("列表") || value.contains("全部")
                    || value.contains("所有") || value.contains("种类") || value.contains("分类") || value.contains("主线")
                    || value.contains("章节") || value.contains("肉鸽") || value.contains("集成战略") || CHAPTER_PATTERN.matcher(value).find();
        }
    }

    private enum QueryMode {
        OPERATOR("干员档案", "干员一览", List.of("基础档案", "综合体检测试", "客观履历", "临床诊断分析"), List.of("operator")),
        ENEMY("敌人档案", "敌人一览", List.of("敌人资料", "能力", "行动方式"), List.of("enemy")),
        ITEM("道具资料", "道具一览", List.of("用途", "获得方式", "材料掉落"), List.of("item")),
        RIIC("后勤技能", "后勤技能一览", List.of("后勤技能"), List.of("logistics", "base")),
        STAGE("关卡资料", "关卡一览", List.of("推荐等级", "首次掉落", "常规掉落"), List.of("stage")),
        STORY("剧情记录", "剧情一览", List.of("剧情", "行动前", "行动后", "剧情记录"), List.of("story")),
        SKIN("时装资料", "时装回廊", List.of("时装", "皮肤", "获取方式"), List.of("outfit", "skin")),
        RECRUIT("公开招募", "公开招募", List.of("公开招募", "标签"), List.of("recruit")),
        GENERAL("综合查询", "干员一览", List.of(), List.of("general"));

        private final String displayName;
        private final String defaultPage;
        private final List<String> sectionKeywords;
        private final List<String> aliases;

        QueryMode(String displayName, String defaultPage, List<String> sectionKeywords, List<String> aliases) {
            this.displayName = displayName;
            this.defaultPage = defaultPage;
            this.sectionKeywords = sectionKeywords;
            this.aliases = aliases;
        }

        private String primaryAlias() {
            return aliases.get(0);
        }

        private List<String> semanticTerms(String keyword) {
            LinkedHashSet<String> terms = new LinkedHashSet<>();
            if (keyword == null || keyword.isBlank()) {
                terms.add(defaultPage);
                return new ArrayList<>(terms);
            }
            if (this == STAGE) {
                appendStageTerms(keyword, terms);
            } else if (this == SKIN) {
                appendSkinTerms(keyword, terms);
            } else if (keyword.contains("种类") || keyword.contains("全部") || keyword.contains("所有") || keyword.contains("一览")) {
                terms.add(defaultPage);
            }
            terms.add(keyword);
            return new ArrayList<>(terms);
        }

        private void appendStageTerms(String keyword, LinkedHashSet<String> terms) {
            Matcher chapter = CHAPTER_PATTERN.matcher(keyword);
            if (chapter.find() || keyword.contains("主线")) {
                String chapterName = chapter.find(0) ? toChineseChapter(chapter.group(1)) : "";
                if (!chapterName.isBlank()) {
                    terms.add("主线关卡/第" + chapterName + "章");
                    terms.add("第" + chapterName + "章");
                    terms.add("主线第" + chapterName + "章");
                }
                terms.add("主线关卡");
                terms.add("模板:关卡导航");
                terms.add("关卡一览");
            }
            if (keyword.contains("肉鸽") || keyword.contains("集成战略")) {
                terms.add("集成战略");
                terms.add("集成战略/关卡");
                terms.add("傀影与猩红孤钻");
                terms.add("水月与深蓝之树");
                terms.add("探索者的银凇止境");
                terms.add("萨卡兹的无终奇语");
            }
        }

        private void appendSkinTerms(String keyword, LinkedHashSet<String> terms) {
            if (keyword.contains("夏卉") || keyword.toUpperCase().contains("FA")) {
                terms.add("时装回廊/珊瑚海岸");
            }
            terms.add("时装回廊");
        }
        private List<String> fallbackItems(String keyword) {
            if (this == STAGE && keyword != null && (keyword.contains("肉鸽") || keyword.contains("集成战略"))) {
                return List.of("傀影与猩红孤钻", "水月与深蓝之树", "探索者的银凇止境", "萨卡兹的无终奇语");
            }
            return List.of();
        }

        private static List<QueryMode> orderedModes() {
            return List.of(OPERATOR, ENEMY, ITEM, RIIC, STAGE, STORY, SKIN, RECRUIT);
        }
    }

    public enum CardType {
        GENERIC, OPERATOR, ENEMY, RECRUIT
    }

    public static class PRTSData {
        public String query;
        public String keyword;
        public String modeKeyword;
        public String modeName;
        public String pageTitle;
        public String sourceUrl;
        public String summary;
        public String imageUrl;
        public String imagePath;
        public boolean operatorOverview;
        public boolean listOverview;
        public CardType cardType = CardType.GENERIC;
        public List<PRTSRow> rows = new ArrayList<>();
        public List<String> relatedTitles = new ArrayList<>();
        public List<PRTSSkill> skills = new ArrayList<>();
        public List<PRTSImage> images = new ArrayList<>();

        public boolean hasContent() {
            return (summary != null && !summary.isBlank()) || imagePath != null || !rows.isEmpty()
                    || !relatedTitles.isEmpty() || !skills.isEmpty() || !images.isEmpty();
        }
    }

    public record PRTSRow(String label, String value) {
    }

    public record PRTSSkill(String name, String meta, String description, String imagePath) {
    }

    public record PRTSImage(String name, String imagePath) {
    }
}
