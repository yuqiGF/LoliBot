package com.bot.service;

import com.bot.service.QuoteStore.QuoteMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void isolatesGroupsAndPreservesSafeExpressionSegments() {
        QuoteStore store = store();
        int count = store.add(100L, "啾咪",
                "[CQ:at,qq=1]宇崎崎超级可爱[CQ:face,id=14]", 1L);
        assertEquals(1, count);

        QuoteMatch match = store.randomMatch(100L, "今天也要啾咪");
        assertNotNull(match);
        assertFalse(match.message().contains("[CQ:at,"));
        assertTrue(match.message().contains("宇崎崎超级可爱"));
        assertTrue(match.message().contains("[CQ:face,id=14]"));
        assertNull(store.randomMatch(200L, "啾咪"));

        QuoteStore restored = store();
        assertNotNull(restored.randomMatch(100L, "重启后继续啾咪"));
        assertNull(restored.randomMatch(200L, "啾咪"));
    }

    @Test
    void supportsConcurrentPendingSessionsForDifferentUsers() {
        QuoteCaptureState state = new QuoteCaptureState();
        state.begin(100L, 1L, "甲");
        state.begin(100L, 2L, "乙");

        assertEquals("甲", state.current(100L, 1L).keyword());
        assertEquals("乙", state.current(100L, 2L).keyword());

        state.complete(100L, 1L, "第一条");
        assertTrue(state.shouldSkipOtherHandlers(100L, 1L, "第一条"));
        assertEquals("乙", state.current(100L, 2L).keyword());
    }

    private QuoteStore store() {
        QuoteStore store = new QuoteStore();
        ReflectionTestUtils.setField(store, "dataFile", tempDir.resolve("quotes.json").toString());
        ReflectionTestUtils.setField(store, "imageDir", tempDir.resolve("images").toString());
        store.load();
        return store;
    }
}