package com.bot.task;

import com.bot.utils.crawler.BangumiCrawler;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Configuration
public class TodayAnime {
    // 注入 Bot 容器
    @Resource
    private BotContainer botContainer;

    @Scheduled(cron = "0 0 0 * * *")
    public void updateTodayAnime(){
        // 机器人账号
        long botId = 2419274814L;
        // 通过机器人账号取出 Bot 对象
        Bot bot = botContainer.robots.get(botId);
        if (bot == null) {
            System.err.println("未找到机器人实例，无法发送今日新番信息");
            return;
        }
        
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
            bot.sendGroupMsg(924171013L, combinedMsg.build(), false);
        } else {
            // 如果没有分割成功，使用原来的方式发送
            String msg = MsgUtils.builder().text(todayAnime).build();
            bot.sendGroupMsg(924171013L, msg, false);
        }
    }
}
