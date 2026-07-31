package com.bot.utils.crawler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bot.utils.common.RichCardRenderer.Card;
import com.bot.utils.common.RichCardRenderer.Field;
import com.bot.utils.common.RichCardRenderer.Section;
import com.bot.utils.common.WebImageUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 百度百科移动词条爬虫。 */
public class BaiduBaikeCrawler {
    private static final Logger logger = LoggerFactory.getLogger(BaiduBaikeCrawler.class);
    private static final String BASE = "https://wapbaike.baidu.com/item/";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of("Asia/Shanghai"));

    public Card query(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String url = BASE + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .header("Referer", "https://www.baidu.com/")
                    .GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
            Card card = parse(keyword.trim(), response.uri().toString(), response.body());
            if (card != null && ("验证".equals(card.title()) || card.summary().startsWith("百度百科是一部内容开放"))) {
                logger.warn("百度百科触发访问验证，keyword={}", keyword);
                return null;
            }
            return card;
        } catch (Exception e) {
            logger.warn("百度百科查询失败，keyword={}, message={}", keyword, e.getMessage());
            return null;
        }
    }

    Card parse(String keyword, String sourceUrl, String html) {
        Document document = Jsoup.parse(html, sourceUrl);
        JSONObject pageData = pageData(document);
        String title = firstNonBlank(
                pageData == null ? null : pageData.getString("lemmaTitle"),
                text(document.selectFirst("[class*=lemmaTitle]")),
                document.title().replace("_百度百科", ""), keyword
        );
        String shortDescription = firstNonBlank(
                pageData == null ? null : pageData.getString("lemmaDesc"),
                text(document.selectFirst("[class*=lemmaDesc]"))
        );
        Element summaryElement = document.selectFirst("[data-region=summary]");
        if (summaryElement != null) {
            summaryElement = summaryElement.clone();
            summaryElement.select("sup, [class*=refSup]").remove();
        }
        String summary = text(summaryElement);
        if (summary.isBlank()) {
            Element meta = document.selectFirst("meta[name=description]");
            summary = meta == null ? "" : meta.attr("content");
        }
        summary = limit(summary.replaceAll("\\[\\d+]", "").replaceAll("\\s+", " ").trim(), 900);
        if (summary.isBlank() && shortDescription.isBlank()) return null;

        List<Field> fields = new ArrayList<>();
        for (Element item : document.select("[class*=cardItem]")) {
            String name = text(item.selectFirst("[class*=cardName]"));
            String value = text(item.selectFirst("[class*=cardValue]"));
            if (!name.isBlank() && !value.isBlank() && fields.stream().noneMatch(it -> it.name().equals(name))) {
                fields.add(new Field(limit(name, 20), limit(value, 120)));
                if (fields.size() >= 12) break;
            }
        }

        if (pageData != null && pageData.getLong("updateTime") != null) {
            fields.add(new Field("最近更新", DATE.format(Instant.ofEpochSecond(pageData.getLongValue("updateTime")))));
        }
        String imageUrl = firstImage(pageData, document);
        String imagePath = WebImageUtils.download(imageUrl, "baike_" + Integer.toHexString(title.hashCode()));
        List<Section> sections = shortDescription.isBlank()
                ? List.of()
                : List.of(new Section("词条定位", shortDescription));
        return new Card(title, "百度百科", summary, imagePath, "2F7FD3",
                fields, sections, "", List.of(), sourceUrl);
    }

    private static JSONObject pageData(Document document) {
        try {
            Element nextData = document.selectFirst("script#__NEXT_DATA__");
            if (nextData == null) return null;
            JSONObject root = JSON.parseObject(nextData.data());
            JSONObject props = root.getJSONObject("props");
            JSONObject pageProps = props == null ? null : props.getJSONObject("pageProps");
            return pageProps == null ? null : pageProps.getJSONObject("pageData");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 优先使用词条概述图或相册封面。DOM 的首个 preload 可能是站点资源，
     * 只有结构化数据没有图片时才回退到页面中的百科图片节点。
     */
    private static String firstImage(JSONObject pageData, Document document) {
        if (pageData != null) {
            JSONObject abstractAlbum = pageData.getJSONObject("abstractAlbum");
            JSONObject cover = abstractAlbum == null ? null : abstractAlbum.getJSONObject("coverPic");
            String url = cover == null ? null : cover.getString("url");
            if (url != null && !url.isBlank()) return url;

            var albums = pageData.getJSONArray("albums");
            if (albums != null && !albums.isEmpty()) {
                JSONObject firstAlbum = albums.getJSONObject(0);
                cover = firstAlbum == null ? null : firstAlbum.getJSONObject("coverPic");
                url = cover == null ? null : cover.getString("url");
                if (url != null && !url.isBlank()) return url;
            }
        }

        Element image = document.selectFirst(
                "[data-region=album] img[src*=bkimg], [class*=swiper] img[src*=bkimg], img[src*=bkimg]");
        return image == null ? null : image.attr("abs:src");
    }

    private static String text(Element element) {
        return element == null ? "" : element.text().replaceAll("\\s+", " ").trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private static String limit(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, length - 3) + "...";
    }
}