package com.bot.utils.common;

import com.bot.model.JapaneseWordCard;
import com.bot.model.JapaneseWordCard.Example;
import com.bot.model.JapaneseWordCard.Form;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** 用 Typst 生成面向日语初学者的单词学习卡。 */
public final class JapaneseWordCardRenderer {
    private static final Logger logger = LoggerFactory.getLogger(JapaneseWordCardRenderer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String AUTHOR = "宇崎崎捏";

    private JapaneseWordCardRenderer() {
    }

    public static File render(JapaneseWordCard card, String typstPath, String fontPath, String cacheKey) {
        if (card == null) return null;
        try {
            Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "lolibot_moji_cards");
            Files.createDirectories(workDir);
            String id = Integer.toHexString((card.word() + "_" + cacheKey).hashCode());
            return TypstRenderUtils.compileToPng(
                    typstPath, fontPath, workDir, "moji_" + id, buildTypst(card), TIMEOUT
            );
        } catch (Exception e) {
            logger.warn("日语学习卡片渲染失败，word={}", card.word(), e);
            return null;
        }
    }

    /** 独立生成源码，便于单元测试和部署前视觉检查。 */
    static String buildTypst(JapaneseWordCard card) {
        StringBuilder out = new StringBuilder();
        out.append("#set page(width: 720pt, height: auto, margin: 0pt, fill: rgb(\"EEF2F1\"))\n");
        out.append("#set text(font: (\"Microsoft YaHei\", \"Noto Sans CJK SC\", \"Noto Sans CJK JP\", \"Source Han Sans SC\", \"Arial\"), size: 10.5pt, fill: rgb(\"202624\"))\n");
        out.append("#let green = rgb(\"168C80\")\n");
        out.append("#let coral = rgb(\"E56B5D\")\n");
        out.append("#let muted = rgb(\"68736F\")\n");
        out.append("#let rule = 0.7pt + rgb(\"D4DDDA\")\n");
        out.append("#let panel(body) = rect(width: 100%, fill: rgb(\"F8FAF9\"), stroke: rule, radius: 4pt, inset: 14pt, body)\n");
        out.append("#rect(width: 720pt, fill: white, stroke: rule, radius: 5pt, inset: 0pt)[\n");
        out.append("#grid(columns: (1fr, auto), align: horizon,\n");
        out.append("  rect(width: 100%, height: 9pt, fill: green),\n");
        out.append("  rect(width: 96pt, height: 9pt, fill: coral)\n");
        out.append(")\n#pad(25pt)[\n");

        out.append("#grid(columns: (1fr, auto), align: horizon,\n");
        out.append("  [#text(size: 11pt, weight: \"bold\", fill: green)[MOJI · 日语入门学习卡]],\n");
        out.append("  [");
        if (!blank(card.level()) && !"—".equals(card.level())) {
            out.append("#rect(fill: rgb(\"E7F4F1\"), radius: 3pt, inset: (x: 8pt, y: 4pt))[#text(size: 9pt, weight: \"bold\", fill: green)[")
                    .append(esc(card.level())).append("]]#h(6pt)");
        }
        if (card.common()) {
            out.append("#rect(fill: rgb(\"FDEDEA\"), radius: 3pt, inset: (x: 8pt, y: 4pt))[#text(size: 9pt, weight: \"bold\", fill: coral)[常用词]]");
        }
        out.append("]\n)#v(23pt)\n");

        out.append("#align(center)[\n");
        out.append("#text(size: 42pt, weight: \"bold\")[").append(esc(card.word())).append("]#v(5pt)\n");
        if (!blank(card.reading()) && !card.reading().equals(card.word())) {
            out.append("#text(size: 16pt, fill: green)[").append(esc(card.reading())).append("]#v(7pt)\n");
        }
        out.append("#text(size: 11pt, fill: muted)[").append(esc(card.partOfSpeech())).append("]\n");
        out.append("]#v(22pt)#line(length: 100%, stroke: rule)#v(17pt)\n");

        out.append("#grid(columns: (1.18fr, 0.82fr), gutter: 14pt, align: top,\n");
        out.append("[\n");
        appendSectionTitle(out, "01", "先记住这个意思");
        out.append("#panel[\n");
        if (card.meanings().isEmpty()) {
            out.append("#text(fill: muted)[暂未取得中文释义]\n");
        } else {
            for (int i = 0; i < card.meanings().size(); i++) {
                out.append("#grid(columns: (22pt, 1fr), gutter: 7pt, align: top,\n");
                out.append("  [#circle(radius: 10pt, fill: green)[#align(center + horizon)[#text(size: 8pt, weight: \"bold\", fill: white)[")
                        .append(i + 1).append("]]]],\n");
                out.append("  [#text(size: 11.5pt, weight: ").append(i == 0 ? "\"bold\"" : "\"regular\"")
                        .append(")[").append(esc(card.meanings().get(i))).append("]]\n)#v(7pt)\n");
            }
        }
        out.append("]#v(15pt)\n");

        appendSectionTitle(out, "02", "放进句子里");
        if (card.examples().isEmpty()) {
            out.append("#panel[#text(fill: muted)[例句讲解暂时不可用，请先学习上方核心义。]]\n");
        } else {
            for (Example example : card.examples()) {
                out.append("#panel[\n");
                out.append("#text(size: 12pt, weight: \"bold\")[").append(esc(example.japanese())).append("]#v(5pt)\n");
                out.append("#text(size: 10pt, fill: muted)[").append(esc(example.chinese())).append("]\n");
                out.append("]#v(8pt)\n");
            }
        }
        out.append("],\n");

        out.append("[\n");
        appendSectionTitle(out, "03", "最常用的变化");
        out.append("#panel[\n");
        if (card.forms().isEmpty()) {
            out.append("#text(fill: muted)[这个词通常不需要词形变化。]\n");
        } else {
            for (Form form : card.forms()) {
                out.append("#grid(columns: (66pt, 1fr), gutter: 7pt,\n");
                out.append("  [#text(size: 9pt, fill: muted)[").append(esc(form.label())).append("]],\n");
                out.append("  [#text(size: 10.5pt, weight: \"bold\")[").append(esc(form.value())).append("]]\n)#v(7pt)\n");
            }
        }
        out.append("]#v(15pt)\n");

        appendSectionTitle(out, "04", "初学提示");
        out.append("#rect(width: 100%, fill: rgb(\"FFF4F1\"), stroke: 0.7pt + rgb(\"F2C9C2\"), radius: 4pt, inset: 14pt)[\n");
        out.append("#text(size: 10.5pt)[").append(esc(blank(card.note()) ? "先记住核心意思，再从第一条例句开始模仿。" : card.note())).append("]\n]");
        out.append("\n]\n)#v(18pt)\n");

        out.append("#line(length: 100%, stroke: rule)#v(8pt)\n");
        out.append("#grid(columns: (1fr, auto), align: horizon,\n");
        out.append("  [#text(size: 8pt, fill: muted)[词典：Jisho / JMdict");
        if (!blank(card.source())) out.append(" · ").append(esc(card.source()));
        out.append("]],\n");
        out.append("  [#text(size: 8.5pt, weight: \"bold\", fill: green)[").append(AUTHOR).append("]]\n");
        out.append(")\n]\n]\n");
        return out.toString();
    }

    private static void appendSectionTitle(StringBuilder out, String number, String title) {
        out.append("#text(size: 9pt, weight: \"bold\", fill: coral)[").append(number).append("]#h(7pt)");
        out.append("#text(size: 13pt, weight: \"bold\")[").append(esc(title)).append("]#v(8pt)\n");
    }

    private static String esc(String value) {
        String text = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                out.append(" #linebreak() ");
                continue;
            }
            if ("\\[]#$%*_`<>@".indexOf(ch) >= 0) out.append('\\');
            out.append(ch);
        }
        return out.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}