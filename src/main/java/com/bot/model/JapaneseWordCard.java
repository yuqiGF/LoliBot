package com.bot.model;

import java.util.List;

/** 日语单词学习卡片的完整结构化数据。 */
public record JapaneseWordCard(
        String word,
        String reading,
        String partOfSpeech,
        String level,
        boolean common,
        List<String> meanings,
        List<Form> forms,
        List<Example> examples,
        String note,
        String source
) {
    public JapaneseWordCard {
        meanings = meanings == null ? List.of() : List.copyOf(meanings);
        forms = forms == null ? List.of() : List.copyOf(forms);
        examples = examples == null ? List.of() : List.copyOf(examples);
    }

    public record Form(String label, String value) {
    }

    public record Example(String japanese, String chinese) {
    }
}
