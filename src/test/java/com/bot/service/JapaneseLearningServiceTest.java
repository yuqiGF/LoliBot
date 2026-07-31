package com.bot.service;

import com.bot.model.JapaneseWordCard;
import com.bot.utils.crawler.JapaneseDictionaryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JapaneseLearningServiceTest {
    private JapaneseDictionaryClient dictionary;
    private DashScopeService model;
    private JapaneseLearningService service;
    private JapaneseDictionaryClient.DictionaryEntry taberu;

    @BeforeEach
    void setUp() {
        dictionary = mock(JapaneseDictionaryClient.class);
        model = mock(DashScopeService.class);
        service = new JapaneseLearningService(dictionary, model);
        taberu = new JapaneseDictionaryClient.DictionaryEntry(
                "食べる", "たべる", List.of("to eat"),
                List.of("Ichidan verb", "Transitive verb"), List.of("N5"), true,
                "https://jisho.org/search/%E9%A3%9F%E3%81%B9%E3%82%8B"
        );
        when(model.localizeJapaneseWord(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {"partOfSpeech":"一段动词 / 他动词","meanings":["吃；食用"],
                 "examples":[
                   {"japanese":"朝ご飯を食べます。","chinese":"我吃早饭。"},
                   {"japanese":"りんごを食べる。","chinese":"吃苹果。"}],
                 "note":"最常见的搭配是“食物 + を + 食べる”。"}
                """);
    }

    @Test
    void randomSelectionCanChooseEveryJlptLevelAndKeepsItsLabel() {
        List<String> levels = List.of("N1", "N2", "N3", "N4", "N5");
        for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
            String level = levels.get(levelIndex);
            AtomicInteger randomCalls = new AtomicInteger();
            int selectedLevel = levelIndex;
            JapaneseLearningService randomService = new JapaneseLearningService(
                    dictionary,
                    model,
                    bound -> randomCalls.getAndIncrement() == 0 ? selectedLevel : 0
            );
            JapaneseDictionaryClient.DictionaryEntry entry = new JapaneseDictionaryClient.DictionaryEntry(
                    "単語" + level, "たんご", List.of("word"), List.of("Noun"),
                    List.of(), false, "https://jisho.org/"
            );
            when(dictionary.queryJlptPage(level, 1)).thenReturn(List.of(entry));

            JapaneseWordCard card = randomService.randomCard(List.of());

            assertNotNull(card, level);
            assertEquals(level, card.level());
            verify(dictionary).queryJlptPage(level, 1);
        }
    }
    @Test
    void buildsBeginnerCardFromDictionaryFacts() {
        when(dictionary.query("食べる")).thenReturn(taberu);

        JapaneseWordCard card = service.lookup("食べる");

        assertNotNull(card);
        assertEquals("たべる", card.reading());
        assertEquals("吃；食用", card.meanings().getFirst());
        assertEquals(2, card.examples().size());
        assertEquals("食べます", card.forms().getFirst().value());
        assertEquals("N5", card.level());
    }

    @Test
    void resolvesChinesePhraseToJapaneseDictionaryForm() {
        when(dictionary.query("吃饭")).thenReturn(null);
        when(model.resolveJapaneseLemma("吃饭")).thenReturn("{\"word\":\"食べる\"}");
        when(dictionary.query("食べる")).thenReturn(taberu);

        JapaneseWordCard card = service.lookup("吃饭");

        assertNotNull(card);
        assertEquals("食べる", card.word());
        verify(model).resolveJapaneseLemma("吃饭");
    }

    @Test
    void rejectsIncompleteLocalizationInsteadOfInventingChineseDetails() {
        JapaneseLearningService.LocalizedContent parsed =
                JapaneseLearningService.parseLocalized("{\"partOfSpeech\":\"\",\"meanings\":[]}");
        assertNull(parsed);

        when(model.localizeJapaneseWord(org.mockito.ArgumentMatchers.anyString())).thenReturn("not-json");
        JapaneseWordCard fallback = service.buildCard(taberu);
        assertFalse(fallback.meanings().isEmpty());
        assertTrue(fallback.meanings().getFirst().startsWith("英文义项："));
    }
}