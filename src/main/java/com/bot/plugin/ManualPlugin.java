package com.bot.plugin;

import com.bot.utils.common.RichCardRenderer;
import com.bot.utils.common.RichCardRenderer.Card;
import com.bot.utils.common.RichCardRenderer.CardItem;
import com.bot.utils.common.RichCardRenderer.Field;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/** 生成总功能介绍页，手册中不暴露群号、QQ 号或内部配置信息。 */
@Component
@Shiro
public class ManualPlugin extends BotPlugin {

    @Value("${typst.path:typst}")
    private String typstPath;

    @Value("${typst.font-path:}")
    private String fontPath;

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^琪琪手册$")
    public void manual(Bot bot, GroupMessageEvent event) {
        File image = RichCardRenderer.render(
                buildManualCard(), typstPath, fontPath, String.valueOf(event.getGroupId()));
        if (image != null) {
            bot.sendGroupMsg(event.getGroupId(),
                    MsgUtils.builder().img("file://" + image.getAbsolutePath()).build(), false);
        } else {
            bot.sendGroupMsg(event.getGroupId(),
                    MsgUtils.builder().text("琪琪手册生成失败，请稍后再试").build(), false);
        }
    }

    /** 独立构造卡片，方便部署前执行 Typst 渲染和视觉检查。 */
    public static Card buildManualCard() {
        return new Card(
                "琪琪手册", "LOLIBOT · COMMAND MANUAL",
                "直接发送下面的指令即可使用。复杂内容会自动整理成清晰的图片。",
                null, "E55371",
                List.of(
                        new Field("聊天", "在群聊中主动 @ 琪琪"),
                        new Field("高级聊天", "指定群已启用上下文与自然接话"),
                        new Field("复读", "所有群均可用"),
                        new Field("主人", "宇崎崎")
                ),
                List.of(),
                "功能入口",
                List.of(
                        new CardItem("PRTS 明日方舟", "prts",
                                "prts 羽毛笔 / prts 13-1\nprts 高级资深干员 近卫", null),
                        new CardItem("动漫资料", "anime",
                                "anime 查看最近更新\nanime 葬送的芙莉莲 查询详情", null),
                        new CardItem("百度百科", "baidu",
                                "baidu 初音未来\n返回带词条主图的百科卡片", null),
                        new CardItem("日语入门学习", "moji",
                                "moji 随机学习 N1-N5 词\nmoji 食べる / moji 吃饭", null),
                        new CardItem("WakaTime", "waka",
                                "waka 查询今日\nwaka week 查询最近七天", null),
                        new CardItem("萌娘百科", "baka",
                                "baka 琪露诺", null),
                        new CardItem("扫雷", "boom",
                                "boom 开始 / A1 翻格\nf A1 标记", null),
                        new CardItem("群语录", "add",
                                "add 啾咪 后发送文字或表情包\n群内出现关键词时随机回复", null),
                        new CardItem("聊天与好感度", "@ / 好感度",
                                "@ 琪琪 发起对话\n好感度 查看本群独立档案", null),
                        new CardItem("AI 扩展服务", "暂不可用",
                                "DeepSeek 问答（ds）\n闻声视频生成", null)
                ),
                ""
        );
    }
}
