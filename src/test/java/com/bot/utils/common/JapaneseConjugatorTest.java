package com.bot.utils.common;

import com.bot.model.JapaneseWordCard.Form;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JapaneseConjugatorTest {

    @Test
    void conjugatesBeginnerVerbGroupsAndIrregularIku() {
        assertForms("食べる", List.of("Ichidan verb"), Map.of(
                "礼貌形", "食べます", "否定形", "食べない", "て形", "食べて"));
        assertForms("書く", List.of("Godan verb with ku ending"), Map.of(
                "礼貌形", "書きます", "て形", "書いて", "可能形", "書ける"));
        assertForms("行く", List.of("Godan verb with ku ending"), Map.of(
                "て形", "行って", "过去形", "行った"));
        assertForms("話す", List.of("Godan verb with su ending"), Map.of("て形", "話して"));
    }

    @Test
    void conjugatesBeginnerAdjectives() {
        assertForms("いい", List.of("I-adjective"), Map.of("否定形", "よくない", "过去形", "よかった"));
        assertForms("静か", List.of("Na-adjective"), Map.of("修饰名词", "静かな", "副词形", "静かに"));
    }

    private static void assertForms(String word, List<String> parts, Map<String, String> expected) {
        Map<String, String> actual = JapaneseConjugator.conjugate(word, parts).stream()
                .collect(Collectors.toMap(Form::label, Form::value));
        expected.forEach((label, value) -> assertEquals(value, actual.get(label), word + " " + label));
    }
}