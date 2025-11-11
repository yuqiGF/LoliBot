package com.bot.utils.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bot.utils.common.HttpClientPool;
import jakarta.annotation.Resource;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;

/**
 * 视频生成器（通义万相）
 */
public class VideoGenerator {

    @Resource
    private VideoGenerator videoGenerator;
    
    private static final Logger logger = LoggerFactory.getLogger(VideoGenerator.class);
    
    private static final String API_CREATE = "https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis";
    private static final String API_QUERY = "https://dashscope.aliyuncs.com/api/v1/tasks/";
    @Value("${langchain4j.community.dashscope.chat-model.api-key}")
    private String API_KEY;
    
    private static final int MAX_POLL_TIMES = 20;
    private static final int POLL_INTERVAL_MS = 15000;
    
    /**
     * 生成视频
     */
    public String generate(String prompt, String imageUrl) {
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            // 创建任务
            String taskId =  createTask(client, prompt, imageUrl);
            if (taskId == null) {
                return null;
            }
            
            // 轮询结果
            return pollResult(client, taskId);
            
        } catch (Exception e) {
            logger.error("视频生成失败", e);
            return null;
        }
    }
    
    /**
     * 创建视频生成任务
     */
    private String createTask(CloseableHttpClient client, String prompt, String imageUrl) {
        try {
            HttpPost post = new HttpPost(API_CREATE);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", "Bearer " + API_KEY);
            post.setHeader("X-DashScope-Async", "enable");
            
            JSONObject request = new JSONObject()
                .fluentPut("model", "wan2.5-i2v-preview")
                .fluentPut("input", new JSONObject()
                    .fluentPut("prompt", cleanPrompt(prompt))
                    .fluentPut("img_url", cleanUrl(imageUrl)))
                .fluentPut("parameters", new JSONObject()
                    .fluentPut("resolution", "720P")
                    .fluentPut("duration", 5)
                    .fluentPut("prompt_extend", true)
                    .fluentPut("audio", true)
                    .fluentPut("seed", -1));
            
            post.setEntity(new StringEntity(request.toJSONString(), StandardCharsets.UTF_8));
            
            HttpResponse response = client.execute(post);
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            
            JSONObject result = JSON.parseObject(body);
            if (result.containsKey("output")) {
                return result.getJSONObject("output").getString("task_id");
            }
            
            logger.error("创建任务失败: {}", body);
            return null;
            
        } catch (Exception e) {
            logger.error("创建任务异常", e);
            return null;
        }
    }
    
    /**
     * 轮询任务结果
     */
    private String pollResult(CloseableHttpClient client, String taskId) {
        try {
            for (int i = 0; i < MAX_POLL_TIMES; i++) {
                Thread.sleep(POLL_INTERVAL_MS);
                
                HttpGet get = new HttpGet(API_QUERY + taskId);
                get.setHeader("Authorization", "Bearer " + API_KEY);
                
                HttpResponse response = client.execute(get);
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                JSONObject result = JSON.parseObject(body);
                JSONObject output = result.getJSONObject("output");
                
                if (output == null) continue;
                
                String status = output.getString("task_status");
                
                if ("SUCCEEDED".equals(status)) {
                    String videoUrl = output.getString("video_url");
                    if (videoUrl == null && output.containsKey("results")) {
                        videoUrl = output.getJSONArray("results")
                            .getJSONObject(0)
                            .getString("video_url");
                    }
                    logger.info("视频生成成功: {}", videoUrl);
                    return videoUrl != null ? videoUrl.trim() : null;
                }
                
                if ("FAILED".equals(status)) {
                    logger.error("视频生成失败");
                    return null;
                }
                
                logger.info("视频生成中... {}/{}", i + 1, MAX_POLL_TIMES);
            }
            
            logger.error("视频生成超时");
            return null;
            
        } catch (Exception e) {
            logger.error("轮询异常", e);
            return null;
        }
    }
    
    /**
     * 清理提示词
     */
    private static String cleanPrompt(String prompt) {
        return prompt == null ? "" : prompt
            .replaceAll("\\[CQ:image[^\\]]*\\]", "")
            .replaceAll("[`'\"]", "")
            .replaceAll("https?://\\S+", "")
            .trim();
    }
    
    /**
     * 清理URL
     */
    private static String cleanUrl(String url) {
        if (url == null) return null;
        return url.trim()
            .replace("&amp;", "&")
            .replaceAll("[`'\"]", "");
    }
}

