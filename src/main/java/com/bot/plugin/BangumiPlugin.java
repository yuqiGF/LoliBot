package com.bot.plugin;

import com.bot.utils.crawler.BangumiCrawler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;

@Component
@Shiro
public class BangumiPlugin extends BotPlugin {
    /**
     * 今日新番
     * @param bot
     * @param event
     * @param matcher
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "今日新番")
    public void todayAnime(Bot bot, GroupMessageEvent event, Matcher matcher){
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

                    int imgIndex = animeItem.indexOf("图片: ");
                    if (imgIndex != -1) {
                        imageUrl = animeItem.substring(imgIndex + "图片: ".length()).trim();
                        textPart = animeItem.substring(0, imgIndex).trim();
                    }

                    // 添加文本内容
                    combinedMsg.text(textPart + "\n");

                    // 使用临时文件方式处理图片（不占用长期磁盘）
                    if (imageUrl != null) {
                        File tmpImg = downloadImageToTemp(imageUrl);
                        if (tmpImg != null) {
                            combinedMsg.img("file://" + tmpImg.getAbsolutePath());
                            tmpImg.delete(); // 发送后立即删除
                        } else {
                            //⭐这张图在服务器里必须有
                            combinedMsg.img("file:///home/admin/loli-bot/error.jpg");

                        }
                    }

                    // 番剧之间添加分隔
                    combinedMsg.text("\n");
                }

                // 添加结束语
                if (todayAnime.contains("到点了，该看番了")) {
                    int endIndex = todayAnime.lastIndexOf("到点了，该看番了");
                    combinedMsg.text(todayAnime.substring(endIndex));
                }

                // 一次性发送复合消息
                bot.sendGroupMsg(event.getGroupId(), combinedMsg.build(), false);

            } else {
                // 如果没有分割成功，使用原来的方式发送
                bot.sendGroupMsg(
                        event.getGroupId(),
                        MsgUtils.builder().text(todayAnime).build(),
                        false
                );
            }
    }
    /**
     * 将网络图片下载为临时文件（低内存占用）
     * 成功返回 File，失败返回 null
     */
    private File downloadImageToTemp(String imageUrl) {
        File tmpFile = null;
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            String contentType = conn.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return null;
            }

            // 创建系统临时文件
            tmpFile = File.createTempFile("bgm_", ".img");

            try (
                    InputStream in = conn.getInputStream();
                    FileOutputStream out = new FileOutputStream(tmpFile)
            ) {
                byte[] buffer = new byte[8192]; // 小 buffer，省内存
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }

            return tmpFile;

        } catch (Exception e) {
            if (tmpFile != null) {
                tmpFile.delete();
            }
            return null;
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
}
