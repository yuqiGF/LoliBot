package com.bot.utils.crawler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bot.utils.common.HttpClientPool;
import com.bot.utils.common.TextMatcher;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 萌娘百科爬虫 — HTML 优先解析（parse API） + wikitext 降级
 * 使用 moegirl.icu 镜像的 API，通用 infobox 检测，支持所有内容类型
 */
public class MoeGirlCrawler {

    private static final String MOEGIRL_URL = "https://moegirl.icu";
    private static final String API_URL = MOEGIRL_URL + "/api.php";
    private static final String FALLBACK_API = "https://zh.moegirl.org.cn/api.php";
    private static final String IMAGE_BASE = "https://box.moegirl.icu/media/";

    private static final String[] INFOBOX_SELECTORS = {
        "table.moe-infobox.infobox2",
        "table.moe-infobox.infoboxSpecial",
        "div.moe-infobox.infotemplatebox",
        "div.infotemplatebox",
        "table.infobox",
        "table.infobox2",
        "table.infoboxSpecial",
        "table[summary*=资料]",
        "table[summary*=信息]",
        "aside",
        "div[itemscope]",
        "[class*=infobox]",
        "[class*=infotemplate]",
        "[class*=infoBox]",
        "[class*=InfoBox]",
    };

    public static class InfoboxData {
        public String pageTitle;
        public String sourceUrl;
        public Map<String, String> fields = new LinkedHashMap<>();
        public List<String> captionLines = new ArrayList<>();
        public List<InfoboxRow> cardRows = new ArrayList<>();
        public String imagePath;
        public String imageUrl;
        public String redirectFrom;
        public boolean isDisambiguation;
        public List<String> disambiguationOptions;
    }

    public static class InfoboxRow {
        public enum Type {
            SECTION,
            FIELD,
            FULL_WIDTH
        }

        public Type type;
        public String label;
        public String value;
        public String backgroundColor;
        public boolean linkLike;

        public static InfoboxRow section(String label, String backgroundColor) {
            InfoboxRow row = new InfoboxRow();
            row.type = Type.SECTION;
            row.label = label;
            row.backgroundColor = backgroundColor;
            return row;
        }

        public static InfoboxRow field(String label, String value, boolean linkLike) {
            InfoboxRow row = new InfoboxRow();
            row.type = Type.FIELD;
            row.label = label;
            row.value = value;
            row.linkLike = linkLike;
            return row;
        }

        public static InfoboxRow fullWidth(String value, String backgroundColor, boolean linkLike) {
            InfoboxRow row = new InfoboxRow();
            row.type = Type.FULL_WIDTH;
            row.value = value;
            row.backgroundColor = backgroundColor;
            row.linkLike = linkLike;
            return row;
        }
    }

    // ==================== 主入口 ====================

    public static InfoboxData getInfo(String characterName) {
        if (characterName == null || characterName.trim().isEmpty()) {
            return null;
        }

        String keyword = characterName.trim();
        System.out.println("[MoeGirl] 查询: " + keyword);

        List<String> candidates = searchCandidates(keyword);
        if (candidates == null || candidates.isEmpty()) {
            System.out.println("[MoeGirl] 未找到相关条目");
            return null;
        }

        String pageTitle = candidates.get(0);
        System.out.println("[MoeGirl] 使用搜索第一结果: " + pageTitle);

        InfoboxData data = fetchAndParse(pageTitle);
        if (data != null) {
            if (!pageTitle.equals(keyword)) {
                data.redirectFrom = keyword;
            }
            return data;
        }

        // 只跟随搜索第一结果自身的重定向，不再尝试后续搜索候选。
        String redirectTarget = resolveRedirect(pageTitle);
        if (redirectTarget != null && !redirectTarget.equals(pageTitle)) {
            System.out.println("[MoeGirl] 重定向: " + pageTitle + " → " + redirectTarget);
            data = fetchAndParse(redirectTarget);
            if (data != null) {
                data.redirectFrom = pageTitle;
                return data;
            }
        }

        System.out.println("[MoeGirl] 搜索第一结果未找到信息卡: " + pageTitle);
        return null;
    }

    private static InfoboxData fetchAndParse(String pageTitle) {
        // 1) 优先用 parse API 获取 HTML
        String html = fetchViaParse(pageTitle);
        if (html != null) {
            // 检查消歧义（即使没有 infobox）
            if (isDisambiguationPage(html, pageTitle)) {
                InfoboxData data = new InfoboxData();
                data.pageTitle = pageTitle;
                data.sourceUrl = MOEGIRL_URL + "/" + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8);
                data.isDisambiguation = true;
                data.disambiguationOptions = parseDisambiguationOptions(html);
                System.out.println("[MoeGirl] 消歧义页, 选项数: "
                        + (data.disambiguationOptions != null ? data.disambiguationOptions.size() : 0));
                return data;
            }

            InfoboxData data = parseInfoboxFromHtml(html);
            if (data != null) {
                finalizeData(data, pageTitle);
                return data;
            }
        }

        // 2) 降级：revisions API 获取 wikitext
        String wikitext = fetchViaRevisions(pageTitle);
        if (wikitext != null) {
            // 检查是否是 wikitext 重定向
            Matcher redirectMatcher = Pattern.compile(
                    "(?i)^\\s*#(?:REDIRECT|redirect|重定向)\\s*\\[\\[([^\\]|#]+)").matcher(wikitext);
            if (redirectMatcher.find()) {
                return null; // 让调用方处理重定向
            }

            // 检查消歧义
            if (isDisambiguationWikitext(wikitext, pageTitle)) {
                InfoboxData data = new InfoboxData();
                data.pageTitle = pageTitle;
                data.sourceUrl = MOEGIRL_URL + "/" + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8);
                data.isDisambiguation = true;
                data.disambiguationOptions = parseDisambiguationWikitext(wikitext);
                System.out.println("[MoeGirl] 消歧义页(wikitext), 选项数: "
                        + (data.disambiguationOptions != null ? data.disambiguationOptions.size() : 0));
                return data;
            }

            InfoboxData data = parseInfoboxFromWikitext(wikitext);
            if (data != null) {
                finalizeData(data, pageTitle);
                return data;
            }
        }

        return null;
    }

    /**
     * 解析重定向目标。先查 wikitext（#REDIRECT [[目标]]），再查 HTML
     */
    private static String resolveRedirect(String pageTitle) {
        // 通过 revisions API 快速检查是否重定向
        String wikitext = fetchViaRevisions(pageTitle);
        if (wikitext != null) {
            Pattern redirectPattern = Pattern.compile(
                    "(?i)^\\s*#(?:REDIRECT|redirect|重定向)\\s*\\[\\[([^\\]|#]+)");
            Matcher rm = redirectPattern.matcher(wikitext);
            if (rm.find()) {
                return rm.group(1).trim();
            }
        }
        // 也检查 parse API 返回的 HTML
        String html = fetchViaParse(pageTitle);
        if (html != null) {
            Document doc = Jsoup.parse(html);
            Element redirectLink = doc.selectFirst(".redirectText a, .mw-redirect a");
            if (redirectLink != null) {
                String title = redirectLink.attr("title");
                if (title != null && !title.isEmpty()) return title;
                String text = redirectLink.text().trim();
                if (!text.isEmpty()) return text;
            }
        }
        return null;
    }

    private static void finalizeData(InfoboxData data, String pageTitle) {
        data.pageTitle = pageTitle;
        data.sourceUrl = MOEGIRL_URL + "/" + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8);

        // 优先使用信息卡内的图片。只有卡片图下载失败/不存在时，才降级到 pageimages。
        if (data.imageUrl != null && !data.imageUrl.isEmpty()) {
            String storageUrl = buildStorageUrlFromImageUrl(data.imageUrl);
            if (storageUrl != null) {
                data.imagePath = downloadImage(storageUrl, pageTitle);
                if (data.imagePath != null) {
                    data.imageUrl = storageUrl;
                }
            }
            if (data.imagePath == null) {
                data.imagePath = downloadImage(data.imageUrl, pageTitle);
            }
        }
        if (data.imagePath == null) {
            String storageUrl = fetchImageUrlFromApi(pageTitle);
            if (storageUrl != null && !storageUrl.isEmpty()) {
                data.imageUrl = storageUrl;
                data.imagePath = downloadImage(storageUrl, pageTitle);
            }
        }
        if (data.imageUrl != null && data.imagePath == null) {
            System.out.println("[MoeGirl] 图片下载失败，将跳过卡片图片");
        }

        System.out.println("[MoeGirl] 解析完成, 字段数: " + data.fields.size()
                + ", imageUrl: " + (data.imageUrl != null ? data.imageUrl : "无")
                + ", imagePath: " + (data.imagePath != null ? data.imagePath : "无")
                + ", 消歧义: " + data.isDisambiguation);
    }

    // ==================== 搜索 ====================

    private static List<String> searchCandidates(String keyword) {
        List<String> result = searchViaApiCandidates(API_URL, keyword);
        if (result == null || result.isEmpty()) {
            result = searchViaApiCandidates(FALLBACK_API, keyword);
        }
        return result;
    }

    private static List<String> searchViaApiCandidates(String baseUrl, String keyword) {
        try {
            String apiUrl = baseUrl + "?action=opensearch&search="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&format=json&limit=15";

            try (CloseableHttpClient client = HttpClientPool.createClient()) {
                HttpGet httpGet = new HttpGet(apiUrl);
                httpGet.setHeader("Accept", "application/json");
                httpGet.setHeader("User-Agent", HttpClientPool.getRandomUserAgent());
                httpGet.setHeader("Referer", baseUrl.replace("/api.php", "/"));

                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    if (response.getStatusLine().getStatusCode() != 200) return null;
                    String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    JSONArray json = JSONArray.parseArray(body);
                    if (json == null || json.size() < 2) return null;

                    JSONArray titles = json.getJSONArray(1);
                    if (titles == null || titles.isEmpty()) return null;

                    List<String> result = new ArrayList<>();
                    for (int i = 0; i < titles.size(); i++) {
                        String t = titles.getString(i);
                        double score = TextMatcher.similarity(keyword, t);
                        System.out.println("[MoeGirl] 搜索候选: " + t
                                + " (rank=" + (i + 1) + ", score=" + String.format("%.2f", score) + ")");
                        result.add(t);
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            System.err.println("[MoeGirl] 搜索异常 (" + baseUrl + "): " + e.getMessage());
            return null;
        }
    }

    // ==================== HTML 获取（parse API） ====================

    private static String fetchViaParse(String pageTitle) {
        String html = fetchParseInternal(pageTitle);
        if (html == null) {
            sleepMs(1000);
            html = fetchParseInternal(pageTitle);
        }
        return html;
    }

    private static String fetchParseInternal(String pageTitle) {
        try {
            String apiUrl = API_URL + "?action=parse&page="
                    + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8)
                    + "&prop=text&format=json";

            try (CloseableHttpClient client = HttpClientPool.createClient()) {
                HttpGet httpGet = new HttpGet(apiUrl);
                httpGet.setHeader("User-Agent", HttpClientPool.getRandomUserAgent());

                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    int status = response.getStatusLine().getStatusCode();
                    if (status != 200) {
                        System.err.println("[MoeGirl] Parse API HTTP " + status);
                        return null;
                    }

                    String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (body.startsWith("<") || body.contains("<html")) {
                        System.err.println("[MoeGirl] Parse API 返回HTML，被拦截");
                        return null;
                    }

                    JSONObject json = JSONObject.parseObject(body);
                    JSONObject parse = json.getJSONObject("parse");
                    if (parse == null) return null;

                    JSONObject text = parse.getJSONObject("text");
                    if (text == null) return null;

                    String html = text.getString("*");
                    if (html == null || html.isEmpty()) return null;

                    System.out.println("[MoeGirl] HTML获取成功 (parse), 长度: " + html.length());
                    return html;
                }
            }
        } catch (Exception e) {
            System.err.println("[MoeGirl] Parse API 异常: " + e.getMessage());
            return null;
        }
    }

    // ==================== HTML infobox 解析 ====================

    private static InfoboxData parseInfoboxFromHtml(String html) {
        Document doc = Jsoup.parse(html);

        Element bestElement = null;
        InfoboxData bestData = null;
        int bestScore = Integer.MIN_VALUE;

        for (Element candidate : collectInfoboxCandidates(doc)) {
            if (shouldSkipInfoboxCandidate(candidate)) continue;

            InfoboxData data = parseInfoboxElement(candidate);
            int score = scoreInfoboxCandidate(candidate, data);
            if (score > bestScore) {
                bestScore = score;
                bestElement = candidate;
                bestData = data;
            }
        }

        if (bestData == null || bestScore < 25) {
            System.out.println("[MoeGirl] HTML中未找到 infobox");
            return null;
        }

        System.out.println("[MoeGirl] 匹配到信息卡候选: <" + bestElement.tagName()
                + "> class=\"" + bestElement.className() + "\" score=" + bestScore);
        return bestData;
    }

    private static Set<Element> collectInfoboxCandidates(Document doc) {
        Set<Element> candidates = new LinkedHashSet<>();
        for (String selector : INFOBOX_SELECTORS) {
            candidates.addAll(doc.select(selector));
        }

        // 兜底扫描结构像信息卡的块，不依赖固定 class 或标签。
        for (Element table : doc.select("table")) {
            if (table.select("th").size() >= 2 || table.select("img").size() > 0) {
                candidates.add(table);
            }
        }
        for (Element block : doc.select("aside, section, div")) {
            int fieldRows = block.select("tr:has(th):has(td), dl:has(dt):has(dd)").size();
            int imgs = block.select("img").size();
            String text = cleanValue(block.text());
            if ((fieldRows >= 2 || (imgs > 0 && fieldRows >= 1))
                    && text.length() <= 6000) {
                candidates.add(block);
            }
        }
        return candidates;
    }

    private static boolean shouldSkipInfoboxCandidate(Element candidate) {
        String marker = (candidate.tagName() + " " + candidate.id() + " "
                + candidate.className() + " " + candidate.attr("role")).toLowerCase();

        boolean explicitlyInfo = marker.contains("info") || marker.contains("templatebox")
                || candidate.hasAttr("itemscope");
        if (marker.contains("mw-parser-output")) return true;
        if (!explicitlyInfo && marker.contains("wikitable")) return true;
        if (!explicitlyInfo && (marker.contains("nav") || marker.contains("toc")
                || marker.contains("footer") || marker.contains("catlinks")
                || marker.contains("ztdh") || marker.contains("collapsible"))) {
            return true;
        }

        String text = cleanValue(candidate.text());
        if (text.length() > 9000) return true;

        int rows = candidate.select("tr").size();
        int fields = candidate.select("tr:has(th):has(td)").size();
        if (!explicitlyInfo && rows > 120) return true;
        return !explicitlyInfo && fields == 0 && candidate.select("img").isEmpty();
    }

    private static InfoboxData parseInfoboxElement(Element infobox) {
        InfoboxData data = new InfoboxData();

        // 提取图片
        Element img = extractInfoboxImage(infobox);
        if (img != null) {
            String src = img.attr("src");
            if (src.isEmpty()) src = img.attr("data-src");
            if (!src.isEmpty()) {
                if (src.startsWith("//")) src = "https:" + src;
                data.imageUrl = normalizeInfoboxImageUrl(src);
            }
        }

        // 解析 key-value 行
        parseInfoboxRows(infobox, data);

        return (data.fields.isEmpty() && data.imageUrl == null) ? null : data;
    }

    private static int scoreInfoboxCandidate(Element candidate, InfoboxData data) {
        if (data == null) return Integer.MIN_VALUE;

        int fields = data.fields.size();
        int sections = 0;
        int fullWidthRows = 0;
        for (InfoboxRow row : data.cardRows) {
            if (row.type == InfoboxRow.Type.SECTION) sections++;
            if (row.type == InfoboxRow.Type.FULL_WIDTH) fullWidthRows++;
        }

        String marker = (candidate.tagName() + " " + candidate.id() + " "
                + candidate.className()).toLowerCase();
        int score = fields * 12 + sections * 4 + fullWidthRows * 3;
        if (isExplicitInfoboxMarker(marker)) score += 240;
        if (data.imageUrl != null) score += 20;
        if (!data.captionLines.isEmpty()) score += 6;
        if (marker.contains("info")) score += 18;
        if (marker.contains("templatebox") || marker.contains("infobox")) score += 20;
        if (candidate.hasAttr("itemscope")) score += 12;

        String text = cleanValue(candidate.text());
        if (text.length() > 5000) score -= 30;
        if (candidate.select("a").size() > Math.max(40, fields * 8 + 20)) score -= 20;
        if (marker.contains("nav") || marker.contains("toc") || marker.contains("ztdh")
                || marker.contains("wikitable") || marker.contains("mw-parser-output")) score -= 120;
        if (fields < 2 && data.imageUrl == null) score -= 40;
        if (fields > 36) score -= 180;
        if (fields > 48) score -= 360;
        if (looksLikeListTable(data)) score -= 420;
        return score;
    }

    private static boolean isExplicitInfoboxMarker(String marker) {
        return marker.contains("moe-infobox")
                || marker.contains("infotemplatebox")
                || marker.contains(" infobox")
                || marker.endsWith("infobox")
                || marker.contains("infoBox".toLowerCase());
    }

    private static boolean looksLikeListTable(InfoboxData data) {
        if (data == null || data.cardRows == null || data.cardRows.size() < 8) return false;

        int fieldCount = 0;
        int listLikeCount = 0;
        for (InfoboxRow row : data.cardRows) {
            if (row.type != InfoboxRow.Type.FIELD) continue;
            fieldCount++;
            String label = row.label == null ? "" : row.label.trim();
            String value = row.value == null ? "" : row.value.trim();
            if (label.matches("^第\\s*\\d+\\s*[话話集期回]$")
                    || label.matches(".*(委员会|学生会|学院|学园|学部|部门|事务局|研究部|开发部|小队|部|团|队|班|室|局|会)$")
                    || countSeparators(value) >= 8) {
                listLikeCount++;
            }
        }
        return fieldCount >= 10 && listLikeCount >= Math.max(6, fieldCount / 2);
    }

    private static int countSeparators(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '、' || c == '·' || c == '•' || c == '?' || c == '，' || c == ',') {
                count++;
            }
        }
        return count;
    }

    private static Element extractInfoboxImage(Element infobox) {
        // 优先查找专用的图片容器
        Element imgContainer = infobox.selectFirst(".infobox-image, .infobox-main-image, .infobox-image-container");
        if (imgContainer != null) {
            Element img = imgContainer.selectFirst("img");
            if (img != null) return img;
        }
        // 取 infobox 内第一个 img
        return infobox.selectFirst("img");
    }

    private static String normalizeInfoboxImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return imageUrl;

        String url = imageUrl.trim();
        String marker = "/media/thumb/";
        int markerIndex = url.indexOf(marker);
        if (url.contains("box.moegirl.icu") && markerIndex >= 0) {
            int fileStart = markerIndex + marker.length();
            int fileEnd = url.lastIndexOf('/');
            if (fileEnd > fileStart) {
                return url.substring(0, markerIndex) + "/media/" + url.substring(fileStart, fileEnd);
            }
        }
        return url;
    }

    private static String buildStorageUrlFromImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.contains("storage.moegirl.org.cn")) {
            return null;
        }

        String marker = "/media/";
        int markerIndex = imageUrl.indexOf(marker);
        if (!imageUrl.contains("box.moegirl.icu") || markerIndex < 0) {
            return null;
        }

        String encodedFileName = imageUrl.substring(markerIndex + marker.length());
        int queryIndex = encodedFileName.indexOf('?');
        if (queryIndex >= 0) {
            encodedFileName = encodedFileName.substring(0, queryIndex);
        }
        int slashIndex = encodedFileName.indexOf('/');
        if (slashIndex >= 0) {
            encodedFileName = encodedFileName.substring(0, slashIndex);
        }
        if (encodedFileName.isBlank()) return null;

        try {
            String fileName = URLDecoder.decode(encodedFileName, StandardCharsets.UTF_8);
            String hash = md5Hex(fileName);
            if (hash == null || hash.length() < 2) return null;
            return "https://storage.moegirl.org.cn/moegirl/commons/"
                    + hash.charAt(0) + "/" + hash.substring(0, 2) + "/" + encodedFileName;
        } catch (Exception e) {
            return null;
        }
    }

    private static String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void parseInfoboxRows(Element infobox, InfoboxData data) {
        Elements rows = topLevelRows(infobox);
        if (rows.isEmpty()) {
            // 非 table 结构：尝试 div 中的 key-value 对
            parseInfoboxDivs(infobox, data);
            return;
        }

        for (Element row : rows) {
            Elements ths = directChildren(row, "th");
            Elements tds = directChildren(row, "td");

            // 节标题行：只有 th 没有 td，或 td[colspan] 的标题行
            if (tds.isEmpty() && ths.size() == 1) {
                String section = cleanCellText(ths.first());
                if (!section.isEmpty()) {
                    data.cardRows.add(InfoboxRow.section(section, extractBackgroundColor(ths.first())));
                }
                continue;
            }
            if (tds.size() == 1 && ths.isEmpty() && tds.first().hasAttr("colspan")) {
                Element td = tds.first();
                if (td.selectFirst("img") != null) {
                    data.captionLines.addAll(extractCaptionLines(td));
                    continue;
                }

                String fullText = cleanCellText(td);
                if (fullText.isEmpty()) continue;

                String backgroundColor = extractBackgroundColor(td);
                boolean looksLikeSection = (td.selectFirst("b, strong") != null || backgroundColor != null)
                        && fullText.length() <= 30
                        && !fullText.contains("：")
                        && !fullText.contains(":")
                        && !fullText.contains("、");
                if (looksLikeSection) {
                    data.cardRows.add(InfoboxRow.section(fullText, backgroundColor));
                } else {
                    data.cardRows.add(InfoboxRow.fullWidth(fullText, backgroundColor, hasVisibleLinks(td)));
                    addDerivedFieldsFromFullLine(fullText, data);
                }
                continue;
            }

            // 跳过没有 key-value 对的行
            if (tds.isEmpty()) continue;

            Element keyElement = ths.isEmpty() ? tds.first() : ths.first();
            Element valueElement = ths.isEmpty() && tds.size() >= 2 ? tds.get(1) : tds.first();

            saveHtmlField(cleanCellText(keyElement), cleanCellText(valueElement),
                    hasVisibleLinks(valueElement), data);
        }
    }

    private static Elements topLevelRows(Element infobox) {
        Element table = infobox.tagName().equalsIgnoreCase("table")
                ? infobox
                : firstOwnTable(infobox);
        Elements rows = new Elements();
        if (table == null) return rows;

        for (Element child : table.children()) {
            if (child.tagName().equalsIgnoreCase("tr")) {
                rows.add(child);
            } else if (child.tagName().equalsIgnoreCase("tbody")
                    || child.tagName().equalsIgnoreCase("thead")
                    || child.tagName().equalsIgnoreCase("tfoot")) {
                for (Element nested : child.children()) {
                    if (nested.tagName().equalsIgnoreCase("tr")) {
                        rows.add(nested);
                    }
                }
            }
        }
        return rows;
    }

    private static Element firstOwnTable(Element element) {
        for (Element child : element.children()) {
            if (child.tagName().equalsIgnoreCase("table")) return child;
        }
        return null;
    }

    private static void saveHtmlField(String key, String value, boolean linkLike, InfoboxData data) {
        if (key == null || value == null) return;

        key = key.trim();
        value = cleanValue(value);
        if (key.isEmpty() || value.isEmpty()) return;

        if (key.startsWith("分类:") || key.startsWith("Category:")) return;
        if (key.length() > 24) return;
        if (key.length() <= 2 && (key.equals("色") || key.equals("发") || key.equals("瞳"))) return;

        data.fields.put(key, value);
        data.cardRows.add(InfoboxRow.field(key, value, linkLike));
    }

    private static Elements directChildren(Element row, String tagName) {
        Elements result = new Elements();
        for (Element child : row.children()) {
            if (child.tagName().equalsIgnoreCase(tagName)) {
                result.add(child);
            }
        }
        return result;
    }

    private static String cleanCellText(Element element) {
        Element clone = element.clone();
        clone.select(".heimu, span.heimu, sup.reference, .reference").remove();
        return cleanValue(clone.text());
    }

    private static List<String> extractCaptionLines(Element mediaCell) {
        List<String> lines = new ArrayList<>();
        Element clone = mediaCell.clone();
        clone.select("a.image, img").remove();

        String text = cleanValue(clone.text());
        if (text.isEmpty()) return lines;

        int authorIdx = text.indexOf("作者");
        if (authorIdx > 0) {
            String first = text.substring(0, authorIdx).trim();
            String second = text.substring(authorIdx).trim();
            if (!first.isEmpty()) lines.add(first);
            if (!second.isEmpty()) lines.add(second);
        } else {
            lines.add(text);
        }
        return lines;
    }

    private static boolean hasVisibleLinks(Element element) {
        Element clone = element.clone();
        clone.select(".heimu, span.heimu").remove();
        return !clone.select("a[href]").isEmpty();
    }

    private static String extractBackgroundColor(Element element) {
        String bgcolor = element.attr("bgcolor");
        if (bgcolor != null && !bgcolor.isBlank()) {
            return normalizeHexColor(bgcolor);
        }

        String style = element.attr("style");
        if (style == null || style.isBlank()) return null;

        Matcher matcher = Pattern.compile("(?i)(?:background(?:-color)?\\s*:\\s*)?(#[0-9a-f]{6})")
                .matcher(style);
        if (matcher.find()) {
            return normalizeHexColor(matcher.group(1));
        }
        return null;
    }

    private static String normalizeHexColor(String color) {
        if (color == null) return null;
        String value = color.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.matches("(?i)[0-9a-f]{6}")) {
            return value.toUpperCase();
        }
        return null;
    }

    private static void addDerivedFieldsFromFullLine(String text, InfoboxData data) {
        String[] parts = text.split("\\s+(?=[^\\s：:]{1,10}[：:])");
        for (String part : parts) {
            int separator = findFirstSeparator(part);
            if (separator <= 0 || separator >= part.length() - 1) continue;

            String key = part.substring(0, separator).trim();
            String value = cleanValue(part.substring(separator + 1).trim());
            if (!key.isEmpty() && key.length() <= 10 && !value.isEmpty()) {
                data.fields.putIfAbsent(key, value);
            }
        }
    }

    private static void parseInfoboxDivs(Element infobox, InfoboxData data) {
        // 处理非 table 的 infobox（如 div.infotemplatebox）
        for (Element dt : infobox.select("dt")) {
            Element valueElement = nextMeaningfulSibling(dt, "dd");
            if (valueElement != null) {
                saveHtmlField(cleanCellText(dt), cleanCellText(valueElement),
                        hasVisibleLinks(valueElement), data);
            }
        }

        for (Element label : infobox.select("[class*=label], [class*=Label], [class*=key], [class*=Key]")) {
            Element valueElement = findValueElement(label);
            if (valueElement != null) {
                saveHtmlField(cleanCellText(label), cleanCellText(valueElement),
                        hasVisibleLinks(valueElement), data);
            }
        }

        if (!data.cardRows.isEmpty()) return;

        // 查找所有包含 key-value 结构的元素
        Elements items = infobox.select("[class*=info-item], [class*=infobox-item], .infobox-row, tr, .row");
        if (items.isEmpty()) {
            // 回退：查找所有 dt/dd 对或有 label/value 结构的元素
            items = infobox.select("dt, dd, [class*=label], [class*=value], [class*=key], [class*=val]");
        }
        // 对于 div 结构，尝试提取其中所有文本行
        if (items.isEmpty()) {
            // 最后的回退：从 div 中提取所有文本，按行解析
            String text = infobox.text();
            for (String line : text.split("\\s{2,}")) {
                line = line.trim();
                int colonIdx = findFirstSeparator(line);
                if (colonIdx > 0 && colonIdx < line.length() - 1) {
                    String key = line.substring(0, colonIdx).trim();
                    String value = cleanValue(line.substring(colonIdx + 1).trim());
                    if (!key.isEmpty() && !value.isEmpty() && key.length() <= 20) {
                        data.fields.put(key, value);
                        data.cardRows.add(InfoboxRow.field(key, value, false));
                    }
                }
            }
        }
    }

    private static Element nextMeaningfulSibling(Element element, String tagName) {
        Element sibling = element.nextElementSibling();
        while (sibling != null) {
            if (sibling.tagName().equalsIgnoreCase(tagName)) return sibling;
            if (!cleanCellText(sibling).isEmpty()) return null;
            sibling = sibling.nextElementSibling();
        }
        return null;
    }

    private static Element findValueElement(Element label) {
        Element parent = label.parent();
        if (parent != null) {
            Element value = parent.selectFirst("[class*=value], [class*=Value], [class*=val], [class*=Val]");
            if (value != null && value != label) return value;
        }

        Element sibling = label.nextElementSibling();
        if (sibling != null) return sibling;
        return null;
    }

    private static int findFirstSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '：' || c == ':' || c == '=') return i;
        }
        return -1;
    }

    // ==================== 数据清洗 ====================

    private static String cleanValue(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        String result = raw;

        // 移除隐藏/heimu 内容
        result = result.replaceAll("(?s)<span[^>]*class=\"[^\"]*heimu[^\"]*\"[^>]*>.*?</span>", "");

        // 移除脚注 [1] [2] 等
        result = result.replaceAll("\\[\\d+\\]", "");

        // 移除 HTML 标签（但保留文本内容）
        result = result.replaceAll("<[^>]+>", "");

        // 解码 HTML 实体
        result = Parser.unescapeEntities(result, false);

        // 移除零宽字符和不可见字符
        result = result.replaceAll("[\\u200B-\\u200F\\uFEFF]", "");

        // 移除多余空白
        result = result.replaceAll("\\s+", " ").trim();

        // 移除占位符
        result = result.replaceAll("[（(]待补充[）)]|\\?\\?\\?", "").trim();

        // 移除纯括号内容
        result = result.replaceAll("^[（(][^）)]*[）)]\\s*", "").trim();

        // 过滤导航/分类文本
        if (result.startsWith("分类:") || result.startsWith("Category:")) return "";

        return result;
    }

    // ==================== 消歧义检测 ====================

    private static boolean isDisambiguationPage(String html, String pageTitle) {
        if (pageTitle.contains("消歧义")) return true;
        Document doc = Jsoup.parse(html);

        // 检查分类链接
        Elements catLinks = doc.select("a[href*=\"Category:\"], a[href*=\"category:\"]");
        for (Element link : catLinks) {
            String text = link.text().trim();
            if (text.contains("消歧义")) return true;
        }

        // 检查页面本体的消歧义模板说明，避免把“另见消歧义页”的普通条目误判为消歧义页。
        String bodyText = doc.text();
        String compactText = bodyText.replaceAll("\\s+", "");
        return compactText.contains("这是一个消歧义页")
                || compactText.contains("本页面是消歧义页")
                || compactText.contains("本页是消歧义页")
                || compactText.contains("此页面是消歧义页")
                || compactText.contains("此页是消歧义页")
                || compactText.contains("消歧义页，罗列")
                || compactText.contains("消歧义页,罗列")
                || compactText.contains("消歧义页，列出")
                || compactText.contains("消歧义页,列出");
    }

    private static List<String> parseDisambiguationOptions(String html) {
        List<String> options = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        // 消歧义列表中，一个 li 往往会同时链接条目和作品名；这里只取每个 li 的第一个条目链接。
        Elements items = doc.select("ul li, .mw-parser-output > ul > li");
        for (Element item : items) {
            Element link = item.selectFirst("a[title]");
            if (link == null) continue;
            String title = link.attr("title");
            if (title.isEmpty()) continue;
            if (title.contains("消歧义") || title.contains("Category:") || title.contains("分类:")) continue;
            if (title.contains("页面不存在")) continue;
            if (title.equals("编辑") || title.equals("讨论") || title.equals("帮助")) continue;
            if (!options.contains(title)) {
                options.add(title);
            }
        }
        return options;
    }

    // ==================== wikitext 降级解析 ====================

    private static boolean isDisambiguationWikitext(String wikitext, String pageTitle) {
        if (pageTitle.contains("消歧义")) return true;
        String lower = wikitext.toLowerCase();
        return wikitext.contains("{{消歧义") || lower.contains("{{disambig")
                || lower.contains("{{disambiguation")
                || wikitext.contains("消歧义页") || wikitext.contains("消歧义页面");
    }

    private static List<String> parseDisambiguationWikitext(String wikitext) {
        List<String> options = new ArrayList<>();
        // 匹配 [[条目名|显示名]] 或 [[条目名]]
        Pattern linkPattern = Pattern.compile("\\[\\[([^\\]|:#]+?)(?:\\|[^\\]]+?)?\\]\\]");
        Matcher m = linkPattern.matcher(wikitext);
        while (m.find()) {
            String title = m.group(1).trim();
            if (title.isEmpty() || title.contains("消歧义") || title.contains("Category:") || title.contains("分类:")) continue;
            if (title.startsWith("File:") || title.startsWith("文件:") || title.startsWith("Image:")) continue;
            if (!options.contains(title)) {
                options.add(title);
            }
        }
        return options;
    }

    private static InfoboxData parseInfoboxFromWikitext(String wikitext) {
        Pattern templateStart = Pattern.compile("\\{\\{([^|}\\n]+)\\n");
        Matcher m = templateStart.matcher(wikitext);

        InfoboxData bestData = null;
        int bestFieldCount = 0;

        while (m.find()) {
            String templateName = m.group(1).trim();

            // 跳过导航/文档/消歧义模板
            if (templateName.contains("/导航") || templateName.contains("/doc")
                    || templateName.contains("导航栏") || templateName.contains("Navbox")
                    || templateName.contains("Disambig") || templateName.contains("disambiguation")
                    || templateName.equals("消歧义")) {
                continue;
            }

            // 从模板名之后开始，计数括号匹配到结束
            int start = m.end();
            int depth = 1;
            int pos = start;
            while (pos < wikitext.length() - 1 && depth > 0) {
                if (wikitext.substring(pos).startsWith("{{")) {
                    depth++;
                    pos += 2;
                } else if (wikitext.substring(pos).startsWith("}}")) {
                    depth--;
                    if (depth == 0) break;
                    pos += 2;
                } else {
                    pos++;
                }
            }

            if (depth != 0) continue;

            String paramsBlock = wikitext.substring(start, pos);

            // 只接受有足够参数的模板
            int paramCount = countParams(paramsBlock);
            if (paramCount < 2) continue;

            InfoboxData data = new InfoboxData();
            parseTemplateParams(paramsBlock, data);

            if (data.fields.size() > bestFieldCount) {
                bestFieldCount = data.fields.size();
                bestData = data;
            }
        }

        // 至少需要3个有效字段才接受
        if (bestData != null && bestData.fields.size() >= 3) {
            return bestData;
        }
        return bestData;
    }

    private static int countParams(String paramsBlock) {
        int count = 0;
        Pattern p = Pattern.compile("^\\|\\s*([^=]+?)\\s*=", Pattern.MULTILINE);
        Matcher m = p.matcher(paramsBlock);
        while (m.find()) count++;
        return count;
    }

    private static void parseTemplateParams(String paramsBlock, InfoboxData data) {
        String[] lines = paramsBlock.split("\n");
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            Pattern kvPattern = Pattern.compile("^\\|\\s*([^=]+?)\\s*=\\s*(.+)$", Pattern.DOTALL);
            Matcher kvMatcher = kvPattern.matcher(trimmed);

            if (kvMatcher.find()) {
                saveParam(currentKey, currentValue.toString(), data);
                currentKey = normalizeKey(kvMatcher.group(1));
                currentValue = new StringBuilder(kvMatcher.group(2));
            } else if (trimmed.startsWith("|")) {
                saveParam(currentKey, currentValue.toString(), data);
                currentKey = normalizeKey(trimmed.substring(1).trim());
                currentValue = new StringBuilder();
            } else {
                if (currentValue.length() > 0) {
                    currentValue.append("\n");
                }
                currentValue.append(trimmed);
            }
        }
        saveParam(currentKey, currentValue.toString(), data);
    }

    private static String normalizeKey(String rawKey) {
        return rawKey.replaceAll("<[^>]+>", "").trim();
    }

    private static void saveParam(String key, String rawValue, InfoboxData data) {
        if (key == null || key.isEmpty()) return;

        // 图片参数
        if (key.equals("image") || key.equals("图片") || key.equals("主图") || key.equals("图")
                || key.equals("images") || key.equals("image-main") || key.equals("main-image")) {
            String filename = cleanWikitextValue(rawValue).trim();
            if (!filename.isEmpty() && !filename.equals("{{PAGENAME}}")) {
                String url = buildImageUrl(filename);
                if (data.imageUrl == null) {
                    data.imageUrl = url;
                }
                System.out.println("[MoeGirl] wikitext图片: " + url);
            }
            return;
        }

        // 跳过样式/元数据字段
        if (key.matches("(?i)^_|color|bgcolor|bg-color|tab|tabs|toggle|image-url|image_url|image-cap|图片说明|标题|titlestyle|headstyle|bodystyle|style|class")) {
            return;
        }

        String cleanValue = cleanWikitextValue(rawValue);
        if (!cleanValue.isEmpty() && cleanValue.length() > 1) {
            data.fields.put(key, cleanValue);
            data.cardRows.add(InfoboxRow.field(key, cleanValue, false));
        }
    }

    private static String cleanWikitextValue(String value) {
        if (value == null || value.isEmpty()) return "";

        String result = value;
        result = result.replaceAll("<[^>]+>", "");
        result = removeNestedTemplates(result);
        result = result.replaceAll("\\[\\[(?:[^|\\]]*\\|)?([^\\]]*?)\\]\\]", "$1");
        result = result.replaceAll("'''([^']*?)'''", "$1");
        result = result.replaceAll("''([^']*?)''", "$1");
        result = result.replaceAll("(?s)<ref[^>]*>.*?</ref>", "");
        result = result.replaceAll("<ref[^>]*/>", "");
        result = result.replaceAll("\\[https?://[^\\s]+\\s+([^\\]]+)\\]", "$1");
        result = result.replaceAll("\\[https?://[^\\s]+\\]", "");
        result = result.replaceAll("\\[\\d+\\]", "");
        result = result.replaceAll("https?://[^\\s]+", "").trim();
        result = result.replaceAll("\\s+", " ").trim();
        result = result.replaceAll("[（(]待补充[）)]|\\?\\?\\?", "").trim();

        return result;
    }

    private static String removeNestedTemplates(String text) {
        while (text.contains("{{")) {
            int start = text.lastIndexOf("{{");
            int end = findMatchingClose(text, start + 2);
            if (end < 0) break;
            text = text.substring(0, start) + text.substring(end + 2);
        }
        return text;
    }

    private static int findMatchingClose(String text, int start) {
        int depth = 1;
        int i = start;
        while (i < text.length() - 1 && depth > 0) {
            if (text.substring(i).startsWith("{{")) {
                depth++;
                i += 2;
            } else if (text.substring(i).startsWith("}}")) {
                depth--;
                if (depth == 0) return i;
                i += 2;
            } else {
                i++;
            }
        }
        return -1;
    }

    private static String buildImageUrl(String filename) {
        String encoded = filename.replace(' ', '_');
        try {
            encoded = URLEncoder.encode(encoded, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
        } catch (Exception ignored) {
        }
        return IMAGE_BASE + encoded;
    }

    // ==================== wikitext 获取（revisions API） ====================

    private static String fetchViaRevisions(String pageTitle) {
        String result = fetchRevisionsInternal(pageTitle);
        if (result == null) {
            sleepMs(1000);
            result = fetchRevisionsInternal(pageTitle);
        }
        return result;
    }

    private static String fetchRevisionsInternal(String pageTitle) {
        try {
            String apiUrl = API_URL + "?action=query&prop=revisions&rvprop=content"
                    + "&titles=" + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8)
                    + "&format=json";

            try (CloseableHttpClient client = HttpClientPool.createClient()) {
                HttpGet httpGet = new HttpGet(apiUrl);
                httpGet.setHeader("User-Agent", HttpClientPool.getRandomUserAgent());

                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    int status = response.getStatusLine().getStatusCode();
                    if (status != 200) {
                        System.err.println("[MoeGirl] Revisions API HTTP " + status);
                        return null;
                    }

                    String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (body.startsWith("<") || body.contains("<html")) {
                        System.err.println("[MoeGirl] Revisions API 返回HTML，被拦截");
                        return null;
                    }

                    JSONObject json = JSONObject.parseObject(body);
                    JSONObject pages = json.getJSONObject("query").getJSONObject("pages");
                    if (pages == null || pages.isEmpty()) return null;

                    String pageId = pages.keySet().iterator().next();
                    JSONObject page = pages.getJSONObject(pageId);
                    JSONArray revisions = page.getJSONArray("revisions");
                    if (revisions == null || revisions.isEmpty()) return null;

                    String content = revisions.getJSONObject(0).getString("*");
                    if (content == null || content.isEmpty()) return null;

                    System.out.println("[MoeGirl] wikitext获取成功 (revisions), 长度: " + content.length());
                    return content;
                }
            }
        } catch (Exception e) {
            System.err.println("[MoeGirl] Revisions API 异常: " + e.getMessage());
            return null;
        }
    }

    // ==================== 图片 URL 获取 ====================

    private static String fetchImageUrlFromApi(String pageTitle) {
        // 先尝试官方 API（可能返回 storage.moegirl.org.cn 域名）
        String source = fetchPageimages(FALLBACK_API, pageTitle);
        // 官方 API 可能对 pageimages 返回 action-notallowed，降级用镜像 API
        if (source == null) {
            source = fetchPageimages(API_URL, pageTitle);
        }
        return source;
    }

    private static String fetchPageimages(String baseUrl, String pageTitle) {
        try {
            String apiUrl = baseUrl + "?action=query&prop=pageimages"
                    + "&titles=" + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8)
                    + "&pithumbsize=800&format=json";

            try (CloseableHttpClient client = HttpClientPool.createClient()) {
                HttpGet httpGet = new HttpGet(apiUrl);
                httpGet.setHeader("User-Agent", HttpClientPool.getRandomUserAgent());

                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    if (response.getStatusLine().getStatusCode() != 200) return null;
                    String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (body.startsWith("<")) return null;

                    JSONObject json = JSONObject.parseObject(body);
                    // 检查是否有错误（如 action-notallowed）
                    if (json.containsKey("error")) return null;

                    JSONObject query = json.getJSONObject("query");
                    if (query == null) return null;
                    JSONObject pages = query.getJSONObject("pages");
                    if (pages == null || pages.isEmpty()) return null;

                    String pageId = pages.keySet().iterator().next();
                    JSONObject page = pages.getJSONObject(pageId);
                    JSONObject thumbnail = page.getJSONObject("thumbnail");
                    if (thumbnail == null) return null;

                    String source = thumbnail.getString("source");
                    if (source == null || source.isEmpty()) return null;

                    // 去除水印参数，获取原图 URL
                    int exclaimIdx = source.indexOf("!/");
                    if (exclaimIdx > 0) {
                        source = source.substring(0, exclaimIdx);
                    }

                    System.out.println("[MoeGirl] pageimages API (" + baseUrl + ") 获取到图片: " + source);
                    return source;
                }
            }
        } catch (Exception e) {
            System.err.println("[MoeGirl] pageimages API 异常 (" + baseUrl + "): " + e.getMessage());
            return null;
        }
    }

    // ==================== 图片下载 ====================

    private static String downloadImage(String imageUrl, String pageTitle) {
        String path = downloadImageInternal(imageUrl, pageTitle, true);
        if (path == null) {
            path = downloadImageInternal(imageUrl, pageTitle, false);
        }
        return path;
    }

    private static String downloadImageInternal(String imageUrl, String pageTitle, boolean withReferer) {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String ext = ".jpg";
            int dotIdx = imageUrl.lastIndexOf('.');
            if (dotIdx > 0) {
                String suffix = imageUrl.substring(dotIdx);
                int queryIdx = suffix.indexOf('?');
                if (queryIdx > 0) suffix = suffix.substring(0, queryIdx);
                if (suffix.length() <= 5) ext = suffix;
            }

            String fileName = "moegirl_" + safePositiveHash(pageTitle + "\n" + imageUrl) + ext;
            String filePath = tempDir + File.separator + fileName;

            File existing = new File(filePath);
            if (existing.exists()) {
                System.out.println("[MoeGirl] 图片缓存命中: " + filePath);
                return filePath;
            }

            try (CloseableHttpClient client = HttpClientPool.createClient()) {
                HttpGet httpGet = new HttpGet(imageUrl);
                httpGet.setHeader("User-Agent", HttpClientPool.getRandomUserAgent());
                if (withReferer) {
                    httpGet.setHeader("Referer", MOEGIRL_URL + "/");
                }

                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    int status = response.getStatusLine().getStatusCode();
                    if (status != 200) {
                        System.err.println("[MoeGirl] 图片下载 HTTP " + status + " (Referer=" + withReferer + ")");
                        return null;
                    }

                    try (InputStream is = response.getEntity().getContent();
                         FileOutputStream fos = new FileOutputStream(filePath)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
            }

            System.out.println("[MoeGirl] 图片已下载: " + filePath);
            return filePath;
        } catch (Exception e) {
            System.err.println("[MoeGirl] 图片下载失败: " + e.getMessage());
            return null;
        }
    }

    // ==================== 工具 ====================

    private static int safePositiveHash(String value) {
        return (value == null ? 0 : value.hashCode()) & 0x7fffffff;
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
