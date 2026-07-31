package com.bot.utils.crawler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JapaneseDictionaryClientTest {

    @Test
    void choosesExactWordInsteadOfEarlierRelatedEntry() {
        String json = """
                {"data":[
                  {"slug":"食べ物","is_common":true,"jlpt":["jlpt-n5"],
                   "japanese":[{"word":"食べ物","reading":"たべもの"}],
                   "senses":[{"english_definitions":["food"],"parts_of_speech":["Noun"]}]},
                  {"slug":"食べる","is_common":true,"jlpt":["jlpt-n5"],
                   "japanese":[{"word":"食べる","reading":"たべる"}],
                   "senses":[{"english_definitions":["to eat","to live on"],
                               "parts_of_speech":["Ichidan verb","Transitive verb"]}]}
                ]}
                """;

        JapaneseDictionaryClient.DictionaryEntry entry = JapaneseDictionaryClient.parse("食べる", json);

        assertNotNull(entry);
        assertEquals("食べる", entry.word());
        assertEquals("たべる", entry.reading());
        assertEquals("to eat", entry.englishDefinitions().getFirst());
        assertEquals("N5", entry.levels().getFirst());
        assertTrue(entry.common());
    }
}