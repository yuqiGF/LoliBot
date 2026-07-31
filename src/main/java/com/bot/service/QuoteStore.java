package com.bot.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按群隔离的关键词语录仓库。
 *
 * <p>一条语录保存 OneBot 消息串，文字、QQ 表情和图片可以混排。QQ 图片链接通常会
 * 过期，因此入库时尽量下载到本地并改写为 file URI；下载失败时仍保留原消息作为降级。</p>
 */
@Component
public class QuoteStore {
    private static final Logger logger = LoggerFactory.getLogger(QuoteStore.class);
    private static final Pattern CQ_SEGMENT =
            Pattern.compile("\\[CQ:([a-zA-Z0-9_-]+),?([^\\]]*)]");
    private static final Pattern URL_PARAM =
            Pattern.compile("(?:^|,)(?:url|file)=([^,\\]]+)");
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;

    private final Map<Long, Map<String, List<QuoteEntry>>> groups = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${quotes.data-file:data/quotes.json}")
    private String dataFile;

    @Value("${quotes.image-dir:data/quote-images}")
    private String imageDir;

    @PostConstruct
    public void load() {
        Path path = Path.of(dataFile).toAbsolutePath();
        if (!Files.isRegularFile(path)) return;
        try {
            Map<Long, Map<String, List<QuoteEntry>>> restored = JSON.parseObject(
                    Files.readString(path, StandardCharsets.UTF_8),
                    new TypeReference<Map<Long, Map<String, List<QuoteEntry>>>>() {
                    });
            if (restored != null) {
                restored.forEach((groupId, values) -> {
                    Map<String, List<QuoteEntry>> keywordMap = new ConcurrentHashMap<>();
                    if (values != null) {
                        values.forEach((keyword, entries) -> {
                            List<QuoteEntry> valid = new CopyOnWriteArrayList<>();
                            if (entries != null) {
                                entries.stream()
                                        .filter(entry -> entry != null && entry.message() != null
                                                && !entry.message().isBlank())
                                        .forEach(valid::add);
                            }
                            if (!valid.isEmpty()) keywordMap.put(keyword, valid);
                        });
                    }
                    if (!keywordMap.isEmpty()) groups.put(groupId, keywordMap);
                });
            }
            logger.info("已恢复 {} 个群的语录数据", groups.size());
        } catch (Exception e) {
            groups.clear();
            logger.warn("语录数据读取失败，将使用空语录库启动: {}", e.getMessage());
        }
    }

    public int add(long groupId, String keyword, String rawMessage, long addedBy) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isBlank() || normalizedKeyword.length() > 30) {
            throw new IllegalArgumentException("关键词长度应为 1 到 30 个字符");
        }
        String prepared = prepareMessage(groupId, rawMessage);
        if (!hasRenderableContent(prepared) || prepared.length() > 8_000) {
            throw new IllegalArgumentException("语录内容为空或过长");
        }

        synchronized (fileLock) {
            Map<String, List<QuoteEntry>> keywordMap =
                    groups.computeIfAbsent(groupId, ignored -> new ConcurrentHashMap<>());
            String actualKeyword = keywordMap.keySet().stream()
                    .filter(existing -> existing.equalsIgnoreCase(normalizedKeyword))
                    .findFirst()
                    .orElse(normalizedKeyword);
            List<QuoteEntry> entries = keywordMap.computeIfAbsent(
                    actualKeyword, ignored -> new CopyOnWriteArrayList<>());
            boolean duplicate = entries.stream().anyMatch(entry -> entry.message().equals(prepared));
            if (!duplicate) {
                entries.add(new QuoteEntry(prepared, addedBy, Instant.now().toEpochMilli()));
                saveLocked();
            }
            return entries.size();
        }
    }

    public boolean hasMatch(long groupId, String plainText) {
        return matchingKeywords(groupId, plainText).size() > 0;
    }

    public QuoteMatch randomMatch(long groupId, String plainText) {
        List<String> keywords = matchingKeywords(groupId, plainText);
        if (keywords.isEmpty()) return null;
        int maxLength = keywords.stream().mapToInt(String::length).max().orElse(0);
        List<String> mostSpecific = keywords.stream()
                .filter(keyword -> keyword.length() == maxLength)
                .toList();
        String keyword = mostSpecific.get(ThreadLocalRandom.current().nextInt(mostSpecific.size()));
        List<QuoteEntry> entries = groups.get(groupId).get(keyword);
        if (entries == null || entries.isEmpty()) return null;
        QuoteEntry entry = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        return new QuoteMatch(keyword, entry.message());
    }

    private List<String> matchingKeywords(long groupId, String plainText) {
        if (plainText == null || plainText.isBlank()) return List.of();
        Map<String, List<QuoteEntry>> keywordMap = groups.get(groupId);
        if (keywordMap == null || keywordMap.isEmpty()) return List.of();
        String normalized = plainText.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<QuoteEntry>> entry : keywordMap.entrySet()) {
            if (!entry.getValue().isEmpty()
                    && normalized.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                result.add(entry.getKey());
            }
        }
        result.sort(Comparator.comparingInt(String::length).reversed());
        return result;
    }

    /**
     * 只保留可安全重放的图片、普通表情和商城表情 CQ 段，移除 @、回复等上下文段。
     */
    private String prepareMessage(long groupId, String rawMessage) {
        if (rawMessage == null) return "";
        Matcher matcher = CQ_SEGMENT.matcher(rawMessage);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String type = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement = switch (type) {
                case "image" -> persistImage(groupId, matcher.group(2), matcher.group());
                case "face", "mface" -> matcher.group();
                default -> "";
            };
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString().trim();
    }

    private String persistImage(long groupId, String params, String fallback) {
        try {
            Matcher urlMatcher = URL_PARAM.matcher(params == null ? "" : params);
            if (!urlMatcher.find()) return fallback;
            String url = urlMatcher.group(1)
                    .replace("&amp;", "&")
                    .replace("\\/", "/")
                    .trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) return fallback;

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET().build();
            HttpResponse<byte[]> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || body == null || body.length == 0 || body.length > MAX_IMAGE_BYTES) {
                return fallback;
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String extension = extension(contentType, url);
            Path directory = Path.of(imageDir, String.valueOf(groupId)).toAbsolutePath();
            Files.createDirectories(directory);
            Path target = directory.resolve(UUID.randomUUID() + extension);
            Files.write(target, body);
            return "[CQ:image,file=" + target.toUri() + "]";
        } catch (Exception e) {
            logger.debug("语录图片本地化失败，保留原 CQ 图片: {}", e.getMessage());
            return fallback;
        }
    }

    private static String extension(String contentType, String url) {
        String lower = (contentType + " " + url).toLowerCase(Locale.ROOT);
        if (lower.contains("gif")) return ".gif";
        if (lower.contains("webp")) return ".webp";
        if (lower.contains("png")) return ".png";
        return ".jpg";
    }

    private static boolean hasRenderableContent(String message) {
        if (message == null || message.isBlank()) return false;
        String plain = CQ_SEGMENT.matcher(message).replaceAll("").trim();
        return !plain.isBlank()
                || message.contains("[CQ:image,")
                || message.contains("[CQ:face,")
                || message.contains("[CQ:mface,");
    }

    private static String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.replaceAll("\\s+", " ").trim();
    }

    private void saveLocked() {
        Path target = Path.of(dataFile).toAbsolutePath();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.writeString(temp, JSON.toJSONString(groups), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            logger.warn("语录数据保存失败: {}", e.getMessage());
        }
    }

    public record QuoteEntry(String message, long addedBy, long addedAt) {
    }

    public record QuoteMatch(String keyword, String message) {
    }
}