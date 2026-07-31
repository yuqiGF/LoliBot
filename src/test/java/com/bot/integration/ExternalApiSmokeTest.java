package com.bot.integration;

import com.bot.plugin.ManualPlugin;
import com.bot.utils.common.RichCardRenderer;
import com.bot.utils.common.RichCardRenderer.Card;
import com.bot.utils.crawler.AnimeClient;
import com.bot.utils.crawler.BaiduBaikeCrawler;
import com.bot.utils.crawler.WakaTimeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 手动启用的真实外部接口冒烟测试，普通构建不会访问网络。 */
@EnabledIfEnvironmentVariable(named = "RUN_EXTERNAL_API_TESTS", matches = "true")
class ExternalApiSmokeTest {

    @Test
    void externalApisAndTypstCardsReturnReasonableData() {
        AnimeClient anime = new AnimeClient();
        Card latest = anime.latestCard();
        assertNotNull(latest, "最近新番日程应有返回");
        assertTrue(latest.items().size() >= 3, "最近更新列表数量应符合常理");
        assertCardRenders(latest, "anime_latest");

        Card animeDetail = anime.detailCard("Frieren: Beyond Journey's End");
        assertNotNull(animeDetail, "英文动漫详情应有返回");
        assertCardRenders(animeDetail, "anime_detail");

        // AniList 不稳定支持中文译名，此断言同时覆盖百度百科别名回退链。
        Card chineseAnime = anime.detailCard("葬送的芙莉莲");
        assertNotNull(chineseAnime, "中文动漫详情应有返回");
        assertTrue(chineseAnime.title().contains("フリーレン")
                        || chineseAnime.title().toLowerCase().contains("frieren"),
                "中文查询应匹配到合理的原名作品");
        assertCardRenders(chineseAnime, "anime_chinese");

        BaiduBaikeCrawler baike = new BaiduBaikeCrawler();
        Card baikeCard = baike.query("初音未来");
        assertNotNull(baikeCard, "百度百科词条应有返回");
        assertTrue(baikeCard.title().contains("初音未来") && baikeCard.summary().length() > 100
                        && baikeCard.imagePath() != null,
                "百科标题和摘要应符合常理");
        assertCardRenders(baikeCard, "baike");

        assertCardRenders(ManualPlugin.buildManualCard(), "manual");

        String wakaKey = System.getenv("WAKATIME_API_KEY");
        assertNotNull(wakaKey, "需要 WAKATIME_API_KEY");
        WakaTimeClient waka = new WakaTimeClient(wakaKey, "Asia/Shanghai");
        String today = waka.getTodayStats();
        String week = waka.getWeeklyStats();
        assertNotNull(today, "WakaTime 今日统计应有返回");
        assertTrue(today.contains("WakaTime 今日") && today.contains("Asia/Shanghai"));
        assertNotNull(week, "WakaTime 七日统计应有返回");
        assertTrue(week.contains("最近 7 天") && week.contains("自然日均"));
    }

    private static void assertCardRenders(Card card, String key) {
        File file = RichCardRenderer.render(card, "typst", "", "smoke_" + key);
        assertNotNull(file, key + " Typst 卡片应成功渲染");
        assertTrue(file.isFile() && file.length() > 10_000, key + " PNG 文件应非空");
    }
}
