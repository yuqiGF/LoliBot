package com.bot.task;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class TodayAnimeTest {

    @Resource
    private TodayAnime todayAnime;

    @Test
    void updateTodayAnime() {
        todayAnime.updateTodayAnime();
    }
}