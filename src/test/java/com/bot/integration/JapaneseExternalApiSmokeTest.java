package com.bot.integration;

import com.bot.model.JapaneseWordCard;
import com.bot.plugin.ManualPlugin;
import com.bot.model.JapaneseWordCard.Example;
import com.bot.utils.common.JapaneseConjugator;
import com.bot.utils.common.JapaneseWordCardRenderer;
import com.bot.utils.common.RichCardRenderer;
import com.bot.utils.crawler.JapaneseDictionaryClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 手动启用的日语词典与 Typst 成图冒烟测试，普通构建不访问外网。 */
@EnabledIfEnvironmentVariable(named = "RUN_JAPANESE_EXTERNAL_TESTS", matches = "true")
class JapaneseExternalApiSmokeTest {

    @Test
    void jishoEntryAndBeginnerCardAreReasonable() {
        JapaneseDictionaryClient.DictionaryEntry entry = new JapaneseDictionaryClient().query("食べる");
        assertNotNull(entry, "Jisho 应返回食べる词条");
        assertEquals("食べる", entry.word());
        assertEquals("たべる", entry.reading());
        assertTrue(entry.partsOfSpeech().stream().anyMatch(value -> value.contains("Ichidan")));

        var n1Page = new JapaneseDictionaryClient().queryJlptPage("N1", 100);
        var n5Page = new JapaneseDictionaryClient().queryJlptPage("N5", 20);
        assertTrue(!n1Page.isEmpty() && n1Page.stream().allMatch(item -> item.levels().contains("N1")),
                "N1 中段分页应返回带 N1 标签的词条");
        assertTrue(!n5Page.isEmpty() && n5Page.stream().allMatch(item -> item.levels().contains("N5")),
                "N5 中段分页应返回带 N5 标签的词条");

        JapaneseWordCard card = new JapaneseWordCard(
                entry.word(), entry.reading(), "一段动词 / 他动词", "N5", entry.common(),
                List.of("吃；食用", "以……为生"),
                JapaneseConjugator.conjugate(entry.word(), entry.partsOfSpeech()),
                List.of(
                        new Example("朝ご飯を食べます。", "我吃早饭。"),
                        new Example("りんごを食べる。", "吃苹果。")
                ),
                "最常见的句型是“食物 + を + 食べる”。先从礼貌形「食べます」开始使用。",
                entry.source()
        );
        File image = JapaneseWordCardRenderer.render(card, "typst", "", "external_smoke");
        assertNotNull(image, "日语入门学习卡应成功渲染");
        assertTrue(image.isFile() && image.length() > 20_000, "成图应包含完整学习内容");

        File manual = RichCardRenderer.render(ManualPlugin.buildManualCard(), "typst", "", "moji_manual_smoke");
        assertNotNull(manual, "新增日语入口后的琪琪手册应成功渲染");
        assertTrue(manual.isFile() && manual.length() > 20_000);
    }
}