package com.bot.plugin;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JapaneseDictionaryPluginTest {
    private static final Pattern COMMAND = Pattern.compile(JapaneseDictionaryPlugin.COMMAND_PATTERN);

    @Test
    void acceptsRandomJapaneseAndChineseLearningQueries() {
        Map<String, String> cases = Map.of(
                "moji", "",
                "moji 食べる", "食べる",
                "食べるmoji", "食べる",
                "moji+吃饭", "吃饭",
                "moji很开心", "很开心"
        );
        cases.forEach((message, expected) -> {
            Matcher matcher = COMMAND.matcher(message);
            assertTrue(matcher.matches(), message);
            assertEquals(expected, JapaneseDictionaryPlugin.extractQuery(matcher), message);
        });
    }

    @Test
    void doesNotStealOrdinaryLatinWords() {
        assertFalse(COMMAND.matcher("emoji").matches());
        assertFalse(COMMAND.matcher("mojito").matches());
    }
}