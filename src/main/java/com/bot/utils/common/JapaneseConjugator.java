package com.bot.utils.common;

import com.bot.model.JapaneseWordCard.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 根据 JMdict 词性生成学习所需的常用基础变形。 */
public final class JapaneseConjugator {
    private static final Map<Character, String> I_ROW = Map.of(
            'う', "い", 'く', "き", 'ぐ', "ぎ", 'す', "し", 'つ', "ち",
            'ぬ', "に", 'ぶ', "び", 'む', "み", 'る', "り"
    );
    private static final Map<Character, String> A_ROW = Map.of(
            'う', "わ", 'く', "か", 'ぐ', "が", 'す', "さ", 'つ', "た",
            'ぬ', "な", 'ぶ', "ば", 'む', "ま", 'る', "ら"
    );
    private static final Map<Character, String> E_ROW = Map.of(
            'う', "え", 'く', "け", 'ぐ', "げ", 'す', "せ", 'つ', "て",
            'ぬ', "ね", 'ぶ', "べ", 'む', "め", 'る', "れ"
    );

    private JapaneseConjugator() {
    }

    public static List<Form> conjugate(String word, List<String> partsOfSpeech) {
        if (word == null || word.isBlank()) return List.of();
        String parts = String.join(" ", partsOfSpeech == null ? List.of() : partsOfSpeech)
                .toLowerCase(Locale.ROOT);
        if (parts.contains("ichidan verb")) return ichidan(word);
        if (parts.contains("suru verb") || word.endsWith("する")) return suru(word);
        if (parts.contains("kuru verb") || word.equals("来る") || word.equals("くる")) return kuru(word);
        if (parts.contains("godan verb")) return godan(word);
        if (parts.contains("i-adjective")) return iAdjective(word);
        if (parts.contains("na-adjective")) return naAdjective(word);
        if (parts.contains("noun")) return noun(word);
        return List.of();
    }

    private static List<Form> ichidan(String word) {
        String stem = removeLastCodePoint(word);
        return List.of(
                new Form("礼貌形", stem + "ます"),
                new Form("否定形", stem + "ない"),
                new Form("て形", stem + "て"),
                new Form("过去形", stem + "た"),
                new Form("可能形", stem + "られる")
        );
    }

    private static List<Form> godan(String word) {
        char ending = word.charAt(word.length() - 1);
        if (!I_ROW.containsKey(ending)) return List.of();
        String stem = word.substring(0, word.length() - 1);
        String te;
        String past;
        if (word.equals("行く") || word.equals("いく")) {
            te = stem + "って";
            past = stem + "った";
        } else if (ending == 'う' || ending == 'つ' || ending == 'る') {
            te = stem + "って";
            past = stem + "った";
        } else if (ending == 'ぬ' || ending == 'ぶ' || ending == 'む') {
            te = stem + "んで";
            past = stem + "んだ";
        } else if (ending == 'く') {
            te = stem + "いて";
            past = stem + "いた";
        } else if (ending == 'ぐ') {
            te = stem + "いで";
            past = stem + "いだ";
        } else {
            te = stem + "して";
            past = stem + "した";
        }
        return List.of(
                new Form("礼貌形", stem + I_ROW.get(ending) + "ます"),
                new Form("否定形", stem + A_ROW.get(ending) + "ない"),
                new Form("て形", te),
                new Form("过去形", past),
                new Form("可能形", stem + E_ROW.get(ending) + "る")
        );
    }

    private static List<Form> suru(String word) {
        String stem = word.substring(0, word.length() - 2);
        return List.of(
                new Form("礼貌形", stem + "します"),
                new Form("否定形", stem + "しない"),
                new Form("て形", stem + "して"),
                new Form("过去形", stem + "した"),
                new Form("可能形", stem + "できる")
        );
    }

    private static List<Form> kuru(String word) {
        if (word.endsWith("来る")) {
            String stem = word.substring(0, word.length() - 2);
            return List.of(
                    new Form("礼貌形", stem + "来ます"),
                    new Form("否定形", stem + "来ない"),
                    new Form("て形", stem + "来て"),
                    new Form("过去形", stem + "来た"),
                    new Form("可能形", stem + "来られる")
            );
        }
        String stem = word.substring(0, Math.max(0, word.length() - 2));
        return List.of(
                new Form("礼貌形", stem + "きます"),
                new Form("否定形", stem + "こない"),
                new Form("て形", stem + "きて"),
                new Form("过去形", stem + "きた"),
                new Form("可能形", stem + "こられる")
        );
    }

    private static List<Form> iAdjective(String word) {
        if (word.equals("いい") || word.equals("良い")) {
            return List.of(
                    new Form("否定形", "よくない"),
                    new Form("过去形", "よかった"),
                    new Form("て形", "よくて"),
                    new Form("副词形", "よく")
            );
        }
        if (!word.endsWith("い")) return List.of();
        String stem = word.substring(0, word.length() - 1);
        return List.of(
                new Form("否定形", stem + "くない"),
                new Form("过去形", stem + "かった"),
                new Form("て形", stem + "くて"),
                new Form("副词形", stem + "く")
        );
    }

    private static List<Form> naAdjective(String word) {
        return List.of(
                new Form("修饰名词", word + "な"),
                new Form("礼貌形", word + "です"),
                new Form("否定形", word + "ではない"),
                new Form("过去形", word + "だった"),
                new Form("副词形", word + "に")
        );
    }

    private static List<Form> noun(String word) {
        List<Form> forms = new ArrayList<>();
        forms.add(new Form("礼貌形", word + "です"));
        forms.add(new Form("否定形", word + "ではありません"));
        forms.add(new Form("过去形", word + "でした"));
        return forms;
    }

    private static String removeLastCodePoint(String value) {
        int end = value.offsetByCodePoints(value.length(), -1);
        return value.substring(0, end);
    }
}
