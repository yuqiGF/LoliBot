package com.bot.utils.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bot.utils.common.HttpClientPool;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * DeepSeek AI 客户端。
 *
 * <p>职责边界：本类只处理 DeepSeek HTTP 协议细节，包括请求体构造、鉴权头设置、
 * 响应解析和异常日志。插件层通过 Spring 注入本客户端，不再手动 new，避免 API Key
 * 无法注入的问题。</p>
 */
@Component
public class DeepSeekClient {

    private static final Logger logger = LoggerFactory.getLogger(DeepSeekClient.class);

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String MODEL = "deepseek-chat";
    private static final String SYSTEM_PROMPT =
            "你是一个超级可爱的猫娘小萝莉，回答尽量简短，保证正常聊天，" +
            "必要时可以展开解释（如上网搜索），每句话末尾加'喵~'，" +
            "可以适当描述当前的动作和神态";

    private final String apiKey;

    public DeepSeekClient(@Value("${deepseek.api-key:${deepseek.api_key:}}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 发送用户消息到 DeepSeek，并返回模型回复。
     */
    public String chat(String message) {
        if (apiKey == null || apiKey.isBlank()) {
            return "DeepSeek API Key 未配置，请联系管理员设置 deepseek.api-key";
        }
        if (message == null || message.isBlank()) {
            return "请在 ds 后面输入想问的内容喵~";
        }

        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpPost post = new HttpPost(API_URL);
            post.setHeader("Content-Type", "application/json; charset=UTF-8");
            post.setHeader("Authorization", "Bearer " + apiKey);
            post.setEntity(new StringEntity(buildRequest(message).toJSONString(), StandardCharsets.UTF_8));

            HttpResponse response = client.execute(post);
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode < 200 || statusCode >= 300) {
                logger.warn("DeepSeek 请求失败，HTTP 状态码: {}, 响应: {}", statusCode, responseBody);
                return "DeepSeek 暂时没有成功响应喵~";
            }

            return parseResponse(responseBody);
        } catch (Exception e) {
            logger.error("DeepSeek 调用异常", e);
            return "出错了喵~ " + e.getMessage();
        }
    }

    /**
     * 构建 DeepSeek Chat Completions 请求体。
     */
    private static JSONObject buildRequest(String message) {
        JSONArray messages = new JSONArray();
        messages.add(new JSONObject()
                .fluentPut("role", "system")
                .fluentPut("content", SYSTEM_PROMPT));
        messages.add(new JSONObject()
                .fluentPut("role", "user")
                .fluentPut("content", message));

        return new JSONObject()
                .fluentPut("model", MODEL)
                .fluentPut("messages", messages);
    }

    /**
     * 从 DeepSeek 响应中取第一条候选回复。
     */
    private static String parseResponse(String responseBody) {
        JSONObject response = JSON.parseObject(responseBody);
        JSONArray choices = response.getJSONArray("choices");

        if (choices != null && !choices.isEmpty()) {
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            if (message != null) {
                String content = message.getString("content");
                if (content != null && !content.isBlank()) {
                    return content;
                }
            }
        }

        return "没有收到回复喵~";
    }
}
