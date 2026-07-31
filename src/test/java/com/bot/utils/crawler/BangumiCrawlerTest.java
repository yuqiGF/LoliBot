package com.bot.utils.crawler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RUN_LEGACY_CRAWLER_TESTS", matches = "true")
class BangumiCrawlerTest {

    @Test
    void getTodayAnime() {
        System.out.println(BangumiCrawler.getTodayAnime());
    }

    @Test
    void sorry(){
        System.out.println("小圆最可爱了");
    }

}
