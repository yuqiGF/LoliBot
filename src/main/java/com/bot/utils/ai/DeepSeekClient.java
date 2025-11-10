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
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;

/**
 * DeepSeek AI 客户端
 */
public class DeepSeekClient {
    
    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    @Value("${deepseek.api_key}")
    private String API_KEY;
    private static final String MODEL = "deepseek-chat";
    
    private static final String SYSTEM_PROMPT = 
        "你是一个超级可爱的猫娘小萝莉，回答尽量简短，保证正常聊天，" +
        "必要时可以展开解释（如上网搜索），每句话末尾加'喵~'，" +
        "可以适当描述当前的动作和神态";
    
    /**
     * 发送消息到 DeepSeek
     */
    public String chat(String message) {
        try (CloseableHttpClient client = HttpClientPool.createClient()) {
            HttpPost post = new HttpPost(API_URL);
            
            // 构建请求
            JSONObject request = buildRequest(message);
            post.setEntity(new StringEntity(request.toJSONString(), StandardCharsets.UTF_8));
            post.setHeader("Content-Type", "application/json; charset=UTF-8");
            post.setHeader("Authorization", "Bearer " + this.API_KEY);
            
            // 执行请求
            HttpResponse response = client.execute(post);
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            
            // 解析响应
            return parseResponse(responseBody);
            
        } catch (Exception e) {
            e.printStackTrace();
            return "出错了喵~ " + e.getMessage();
        }
    }
    
    /**
     * 构建请求体
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
     * 解析响应
     */
    private static String parseResponse(String responseBody) {
        JSONObject response = JSON.parseObject(responseBody);
        JSONArray choices = response.getJSONArray("choices");
        
        if (choices != null && !choices.isEmpty()) {
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            if (message != null) {
                return message.getString("content");
            }
        }
        
        return "没有收到回复喵~";
    }
}

