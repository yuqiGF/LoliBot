package com.bot.utils.common;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** 下载公开网页图片到系统临时目录，并限制大小与内容类型。 */
public final class WebImageUtils {
    private static final Logger logger = LoggerFactory.getLogger(WebImageUtils.class);
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;

    private WebImageUtils() {
    }

    public static String download(String imageUrl, String prefix) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String normalized = imageUrl.startsWith("//") ? "https:" + imageUrl : imageUrl;
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpGet get = new HttpGet(normalized);
            boolean baiduImage = normalized.contains("baidu")
                    || normalized.contains("bcebos.com")
                    || normalized.contains("bdstatic.com");
            get.setHeader("Referer", baiduImage ? "https://baike.baidu.com/" : "https://anilist.co/");
            try (CloseableHttpResponse response = client.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                String contentType = response.getFirstHeader("Content-Type") == null
                        ? "" : response.getFirstHeader("Content-Type").getValue().toLowerCase();
                if (status < 200 || status >= 300 || !contentType.startsWith("image/")) return null;
                byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) return null;
                String extension = contentType.contains("png") ? ".png"
                        : contentType.contains("webp") ? ".webp" : ".jpg";
                Path dir = Path.of(System.getProperty("java.io.tmpdir"), "lolibot_web_images");
                Files.createDirectories(dir);
                Path file = dir.resolve(prefix.replaceAll("[^a-zA-Z0-9_-]", "_") + extension);
                Files.write(file, bytes);
                return file.toAbsolutePath().toString();
            }
        } catch (Exception e) {
            logger.debug("图片下载失败: {}", e.getMessage());
            return null;
        }
    }
}