package com.bot.plugin;

import com.bot.service.DashScopeService;
import com.bot.utils.ai.DeepSeekClient;
import com.bot.utils.ai.VideoGenerator;
import com.bot.utils.crawler.BangumiCrawler;
import com.bot.utils.crawler.MoeGirlCrawler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.enums.AtEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;

@Component
@Shiro
public class MainPlugin extends BotPlugin {

    private static final Logger logger = LoggerFactory.getLogger(MainPlugin.class);

    @Resource
    private DashScopeService dashScopeService;

    //私聊消息
    @PrivateMessageHandler
    @MessageHandlerFilter(cmd = "你好")
    public void test1(Bot bot , PrivateMessageEvent event, Matcher matcher){
        String msg = MsgUtils.builder().text("我喜欢你").build();
        bot.sendPrivateMsg(event.getUserId(),msg,false);
    }

    /**
     * LLM语言模型
     * @param bot
     * @param event
     */
    //群聊消息
    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED) //被at了才会发送
    public void deepSeekTalk(Bot bot, GroupMessageEvent event){
        //用正则表达式把头去掉
        String message = event.getMessage().replaceFirst("\\[CQ:.*?\\]\\s*", "");
//        System.out.println(message);
//        String res = DeepSeekClient.chat(message);
        String res = "";
        if (message.isEmpty()){
            //光艾特 不发消息
            res = "Ciallo～(∠・ω< )⌒★";
        }else {
            //让ai认识发消息的人是谁
            message = message + "|" + event.getUserId();
            res = dashScopeService.chat(message);
        }
        String response = MsgUtils.builder()
                .text(res)
//                .at(event.getUserId())
                .build();
        bot.sendGroupMsg(event.getGroupId(),response,false);
    }

    /**
     * 私聊ai
     * @param bot
     * @param event
     */
    @PrivateMessageHandler
    public void aiTalk(Bot bot, PrivateMessageEvent event){
        String msg = event.getMessage();
        String response = dashScopeService.chat(msg);
        String res = MsgUtils.builder()
                .text(response)
                .build();
        bot.sendPrivateMsg(event.getUserId(),res,false);
    }


    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "啾咪")
    public void kiss(Bot bot,GroupMessageEvent event,Matcher matcher) throws IOException {
        String msg = MsgUtils.builder().text("宇崎崎超级可爱").build();
//        CloseableHttpClient httpClient = HttpClients.createDefault();
//        HttpPost httpPost = new HttpPost("/send_poke");
//        BasicNameValuePair userId = new BasicNameValuePair("user_id", event.getUserId().toString());
//        httpPost.setEntity(new UrlEncodedFormEntity((List<? extends NameValuePair>) userId));
//        httpClient.execute(httpPost);
        bot.sendGroupMsg(event.getGroupId(),msg,false);
    }

    /**
     * 动漫获取
     * @param bot
     * @param event
     * @param matcher
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "今日新番")
    public void todayAnime(Bot bot,GroupMessageEvent event,Matcher matcher){
        String todayAnime = BangumiCrawler.getTodayAnime();
        
        // 创建一个复合消息构建器，用于打包所有新番信息
        MsgUtils combinedMsg = MsgUtils.builder();
        
        // 处理包含图片的新番信息
        String[] animeItems = todayAnime.split("\n\n");
        if (animeItems.length > 0) {
            // 添加标题
            combinedMsg.text(animeItems[0] + "\n\n");
            
            // 处理每个番剧信息
            for (int i = 1; i < animeItems.length; i++) {
                String animeItem = animeItems[i];
                if (animeItem.trim().isEmpty() || animeItem.contains("到点了，该看番了")) {
                    continue;
                }
                
                // 提取文本部分和图片URL
                String textPart = animeItem;
                String imageUrl = null;
                
                // 查找图片URL
                int imgIndex = animeItem.indexOf("图片: ");
                if (imgIndex != -1) {
                    // 提取图片URL
                    imageUrl = animeItem.substring(imgIndex + "图片: ".length()).trim();
                    
                    // 提取文本部分
                    textPart = animeItem.substring(0, imgIndex).trim();
                }
                
                // 添加文本内容
                combinedMsg.text(textPart + "\n");
                
                // 添加图片（如果有）
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    combinedMsg.img(imageUrl);
                }
                
                // 番剧之间添加分隔
                combinedMsg.text("\n");
            }
            
            // 添加结束语
            if (todayAnime.contains("到点了，该看番了")) {
                int endIndex = todayAnime.lastIndexOf("到点了，该看番了");
                String endMsg = todayAnime.substring(endIndex);
                combinedMsg.text(endMsg);
            }
            
            // 一次性发送复合消息
            bot.sendGroupMsg(event.getGroupId(), combinedMsg.build(), false);
        } else {
            // 如果没有分割成功，使用原来的方式发送
            String msg = MsgUtils.builder().text(todayAnime).build();
            bot.sendGroupMsg(event.getGroupId(), msg, false);
        }
    }

    /**
     * 角色搜索
     * @param bot
     * @param event
     * @param matcher
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^loli\s(.*)?$")
    public void test2(Bot bot, GroupMessageEvent event, Matcher matcher) {
        String name = matcher.group(1);
        String info = BangumiCrawler.searchCharacter(name);

        // 安全地处理图片信息
        String textPart = info;
        String imageUrl = null;

        // 查找"图片: "的位置
        int imgIndex = info.lastIndexOf("图片:");
        if (imgIndex != -1) {
            // 提取图片URL（从"图片: "后面开始到字符串结束）
            imageUrl = info.substring(imgIndex + "图片:".length()).trim();

            // 提取图片前面的内容（从开头到"图片: "之前）
            textPart = info.substring(0, imgIndex).trim();

            MsgUtils msg = MsgUtils.builder().text(textPart).img(imageUrl);
            bot.sendGroupMsg(event.getGroupId(), msg.build(), false);
        }else {
            MsgUtils msg = MsgUtils.builder().text(textPart);
            bot.sendGroupMsg(event.getGroupId(), msg.build(), false);
        }
    }

    /**
     * 萌娘百科查询
     * 命令：baka <查询内容>
     * 示例：baka 初音未来
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^baka\\s(.*)?$")
    public void moeGirl(Bot bot, GroupMessageEvent event, Matcher matcher) throws IOException {
        String name = matcher.group(1);
        
        if (name == null || name.trim().isEmpty()) {
            String tip = MsgUtils.builder()
                    .text("使用方法：baka <查询内容>\n")
                    .text("示例：baka 初音未来")
                    .build();
            bot.sendGroupMsg(event.getGroupId(), tip, false);
            return;
        }
        
        try {
            String info = MoeGirlCrawler.getInfo(name);
            
            // 检查是否包含图片链接
            String imageUrl = extractMoeGirlImageUrl(info);
            
            // 去除文本中的图片URL行
            String textInfo = info.replaceFirst("🖼️ 图片：.*?\n", "");
            
            // 构建消息（图片和文本在同一个消息框内）
            StringBuilder messageBuilder = new StringBuilder();
            
            // 如果有图片，将图片CQ码嵌入到文本开头
            if (imageUrl != null && !imageUrl.isEmpty()) {
                messageBuilder.append("[CQ:image,file=").append(imageUrl).append("]\n");
            }
            
            // 添加文本信息
            messageBuilder.append(textInfo);
            
            String msg = MsgUtils.builder()
                    .text(messageBuilder.toString())
                    .build();
            bot.sendGroupMsg(event.getGroupId(), msg, false);
            
        } catch (Exception e) {
            logger.error("萌娘百科查询失败", e);
            String error = MsgUtils.builder()
                    .text("查询失败：" + e.getMessage())
                    .build();
            bot.sendGroupMsg(event.getGroupId(), error, false);
        }
    }
    
    /**
     * 从萌娘百科结果中提取图片URL
     */
    private String extractMoeGirlImageUrl(String info) {
        if (info == null) return null;
        
        String[] lines = info.split("\n");
        for (String line : lines) {
            if (line.startsWith("🖼️ 图片：")) {
                return line.replace("🖼️ 图片：", "").trim();
            }
        }
        return null;
    }


    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^ds\\s(.*)?$")
    public void deepSeekTemp(Bot bot, GroupMessageEvent event, Matcher matcher) throws IOException {
        String name = matcher.group(1);
        DeepSeekClient deepSeekClient = new DeepSeekClient();
        String info = deepSeekClient.chat(event.getMessage());
        String msg = MsgUtils.builder().text(info).build();
        bot.sendGroupMsg(event.getGroupId(),msg,false);
    }
    
    /**
     * 视频生成功能 - 当@机器人并包含视频生成指令时触发
     * 在普通聊天处理器之前处理视频生成请求
     */
    @Resource
    private VideoGenerator videoGenerator;

    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED) // 视频生成处理器
    public void generateVideoOnAt(Bot bot, GroupMessageEvent event) {
        // 提取消息内容，去掉CQ码
        String message = event.getMessage().replaceFirst("\\[CQ:.*?\\]\\s*", "");

        // 检查是否包含视频生成指令
        if (!message.isEmpty() && isVideoGenerationCommand(message)) {
            try {
                logger.info("收到视频生成请求: {}", message);
                
                // 提取消息中的图片URL
                String imgUrl = extractImageUrl(event.getMessage());
                
                if (imgUrl == null) {
                    logger.error("未在消息中找到图片");
                    String errorMsg = MsgUtils.builder()
                            .text("视频生成失败喵~")
                            .text("\n通义万相-图生视频模型需要一张首帧图像和文本描述哦~")
                            .text("\n请在@机器人并发送视频生成指令的同时，附带一张图片作为首帧")
                            .build();
                    bot.sendGroupMsg(event.getGroupId(), errorMsg, false);
                    return;
                }
                
                logger.info("提取到的图片URL: {}", imgUrl);

                // 先生成一个提示消息告知用户正在处理
                String processingMsg = MsgUtils.builder()
                        .text("正在为你生成视频，请稍等喵~")
                        .text("\n图生视频生成过程可能需要1-5分钟，请耐心等待~")
                        .build();
                bot.sendGroupMsg(event.getGroupId(), processingMsg, false);

                // 调用视频生成API（异步调用，会自动轮询获取结果）
                String videoUrl = videoGenerator.generate(message, imgUrl);

                if (videoUrl != null) {
                    logger.info("视频生成成功: {}", videoUrl);
                    // 发送视频链接，移除使用视频URL作为图片的部分
                    String videoMsg = MsgUtils.builder()
                            .text("视频生成完成啦~")
                            .text("\n视频链接：" + videoUrl)
                            .text("\n提示：视频链接有效期可能有限，请及时保存~")
                            .build();
                    bot.sendGroupMsg(event.getGroupId(), videoMsg, false);
                } else {
                    logger.error("视频生成失败");
                    String errorMsg = MsgUtils.builder()
                            .text("视频生成失败了呢，可能的原因：")
                            .text("\n1. 描述可能不够清晰或包含敏感内容")
                            .text("\n2. API调用限制、网络问题或服务繁忙")
                            .text("\n3. 图片格式或大小不符合要求")
                            .text("\n4. 请检查API密钥是否有效")
                            .build();
                    bot.sendGroupMsg(event.getGroupId(), errorMsg, false);
                }

            } catch (Exception e) {
                logger.error("视频生成过程中发生异常: {}", e.getMessage(), e);
                String errorMsg = MsgUtils.builder()
                        .text("视频生成时出错了喵~")
                        .text("\n错误信息：" + e.getMessage())
                        .text("\n请稍后再试或联系管理员查看日志")
                        .build();
                bot.sendGroupMsg(event.getGroupId(), errorMsg, false);
            }
        }

        // 不包含视频生成指令，框架会自动调用其他匹配的处理器（如deepSeekTalk）
    }
    
    /**
     * 检查是否为视频生成命令
     */
    private boolean isVideoGenerationCommand(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("生成视频") || lower.contains("视频生成") || 
               lower.contains("做个视频") || lower.contains("video");
    }
    
    /**
     * 从消息中提取图片URL
     */
    private String extractImageUrl(String message) {
        // 检查消息中是否包含CQ码图片
        if (message.contains("[CQ:image")) {
            try {
                // 查找CQ码图片的URL部分
                int start = message.indexOf("url=") + 4;
                if (start > 3) {
                    // 尝试多种可能的结束标记
                    // 1. 查找逗号（常见于CQ码参数分隔）
                    int end1 = message.indexOf(",", start);
                    // 2. 查找右括号
                    int end2 = message.indexOf(")", start);
                    // 3. 查找右中括号
                    int end3 = message.indexOf("]", start);
                    
                    // 选择最早出现的有效结束标记
                    int end = -1;
                    if (end1 > start) end = end1;
                    if (end2 > start && (end == -1 || end2 < end)) end = end2;
                    if (end3 > start && (end == -1 || end3 < end)) end = end3;
                    
                    if (end > start) {
                        String url = message.substring(start, end);
                        // 处理URL中的转义字符和可能的空格
                        url = url.trim().replace("\\/", "/");
                        // 处理可能的引号包围
                        if (url.startsWith("'") && url.endsWith("'")) {
                            url = url.substring(1, url.length() - 1);
                        } else if (url.startsWith("\"") && url.endsWith("\"")) {
                            url = url.substring(1, url.length() - 1);
                        }
                        return url;
                    }
                }
            } catch (Exception e) {
                logger.error("提取图片URL时发生异常: {}", e.getMessage());
            }
        }
        
        // 检查是否包含普通的图片URL
        // 增强的URL匹配正则表达式，支持更多图片格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "https?:\\/\\/[^,\\s\\]]+\\.(jpg|jpeg|png|gif|bmp|webp|avif|tiff|svg|ico)", 
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }
        
        return null;
    }
}
