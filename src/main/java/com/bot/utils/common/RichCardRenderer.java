package com.bot.utils.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用信息卡片渲染器，供动漫、百科和总手册共用。
 *
 * <p>业务层只提供结构化字段；本类统一页面宽度、字体、配色、图片尺寸和作者标签，
 * 避免每个新功能复制一整套 Typst 字符串。</p>
 */
public final class RichCardRenderer {
    private static final Logger logger = LoggerFactory.getLogger(RichCardRenderer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String AUTHOR = "宇崎崎捏";

    private RichCardRenderer() {
    }

    public static File render(Card card, String typstPath, String fontPath, String cacheKey) {
        if (card == null) return null;
        try {
            Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "lolibot_rich_cards");
            Files.createDirectories(workDir);
            String id = Integer.toHexString((card.title() + cacheKey).hashCode());
            String mainImage = copyImage(card.imagePath(), workDir, "main_" + id);
            List<PreparedItem> items = new ArrayList<>();
            for (int i = 0; i < card.items().size(); i++) {
                CardItem item = card.items().get(i);
                items.add(new PreparedItem(item.title(), item.meta(), item.description(),
                        copyImage(item.imagePath(), workDir, "item_" + id + "_" + i)));
            }
            return TypstRenderUtils.compileToPng(
                    typstPath, fontPath, workDir, "rich_" + id,
                    buildTypst(card, mainImage, items), TIMEOUT
            );
        } catch (Exception e) {
            logger.warn("通用信息卡片渲染失败", e);
            return null;
        }
    }

    private static String copyImage(String sourcePath, Path workDir, String stem) {
        if (sourcePath == null || sourcePath.isBlank()) return null;
        try {
            Path source = Path.of(sourcePath);
            if (!Files.isRegularFile(source)) return null;
            String lower = source.getFileName().toString().toLowerCase();
            String ext = lower.endsWith(".png") ? ".png" : lower.endsWith(".webp") ? ".webp" : ".jpg";
            String fileName = stem + ext;
            Files.copy(source, workDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildTypst(Card card, String mainImage, List<PreparedItem> items) {
        String accent = validColor(card.accent()) ? card.accent() : "18A7A0";
        if ("琪琪手册".equals(card.title())) return buildManualTypst(card, items, accent);
        StringBuilder out = new StringBuilder();
        out.append("#set page(width: 720pt, height: auto, margin: 0pt, fill: rgb(\"F4F6F8\"))\n");
        out.append("#set text(font: (\"Microsoft YaHei\", \"Noto Sans CJK SC\", \"Source Han Sans SC\", \"Arial\"), size: 10pt, fill: rgb(\"20252B\"))\n");
        out.append("#let accent = rgb(\"").append(accent).append("\")\n");
        out.append("#let muted = rgb(\"68717C\")\n");
        out.append("#let rule = 0.6pt + rgb(\"D5DBE1\")\n");
        out.append("#rect(width: 720pt, fill: white, stroke: rule, radius: 5pt, inset: 0pt)[\n");
        out.append("#rect(width: 100%, height: 8pt, fill: accent)\n");
        out.append("#pad(20pt)[\n");
        out.append("#text(size: 24pt, weight: \"bold\")[").append(esc(card.title())).append("]\n");
        if (!blank(card.subtitle())) {
            out.append("#h(8pt)#text(size: 10pt, fill: muted)[").append(esc(card.subtitle())).append("]\n");
        }
        out.append("#v(8pt)#line(length: 100%, stroke: rule)#v(14pt)\n");

        if (mainImage != null) {
            if ("百度百科".equals(card.subtitle())) {
                out.append("#grid(columns: (1fr, 220pt), gutter: 20pt, align: top,\n");
                out.append("  [");
                appendSummaryAndFields(out, card);
                out.append("],\n");
                out.append("  image(").append(str(mainImage))
                        .append(", width: 220pt, height: 236pt, fit: \"contain\")\n");
                out.append(")#v(16pt)\n");
            } else {
                out.append("#grid(columns: (180pt, 1fr), gutter: 18pt, align: top,\n");
                out.append("  image(").append(str(mainImage))
                        .append(", width: 180pt, height: 236pt, fit: \"cover\"),\n");
                out.append("  [");
                appendSummaryAndFields(out, card);
                out.append("]\n)#v(16pt)\n");
            }
        } else {
            appendSummaryAndFields(out, card);
            out.append("#v(12pt)\n");
        }

        List<Section> sections = card.sections();
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            if (isAnimeStaff(section) && i + 1 < sections.size() && isAnimeCast(sections.get(i + 1))) {
                appendAnimePeopleGrid(out, section, sections.get(++i));
                continue;
            }
            appendSection(out, section);
        }

        if (!items.isEmpty()) {
            out.append("#text(size: 14pt, weight: \"bold\", fill: accent)[")
                    .append(esc(blank(card.itemsTitle()) ? "更多信息" : card.itemsTitle())).append("]#v(7pt)\n");
            for (PreparedItem item : items) appendItem(out, item);
        }

        out.append("#v(10pt)#line(length: 100%, stroke: rule)#v(7pt)\n");
        if (!blank(card.source())) {
            out.append("#text(size: 8pt, fill: muted)[来源：").append(esc(card.source())).append("]\n");
        }
        out.append("#align(right)[#text(size: 8pt, weight: \"bold\", fill: accent)[").append(AUTHOR).append("]]\n");
        out.append("]\n]\n");
        return out.toString();
    }

    /** 动漫详情中的职员与角色天然适合横向比较，合并后可显著缩短长图。 */
    private static void appendAnimePeopleGrid(StringBuilder out, Section staff, Section cast) {
        if (blank(staff.body()) || blank(cast.body())) {
            appendSection(out, staff);
            appendSection(out, cast);
            return;
        }
        out.append("#grid(columns: (1fr, 1fr), gutter: 12pt, align: top,\n");
        appendPeoplePanel(out, staff);
        out.append(",\n");
        appendPeoplePanel(out, cast);
        out.append("\n)#v(12pt)\n");
    }

    private static void appendPeoplePanel(StringBuilder out, Section section) {
        out.append("  rect(width: 100%, fill: rgb(\"F7F8FA\"), stroke: rule, radius: 4pt, inset: 12pt)[\n")
                .append("    #text(size: 13pt, weight: \"bold\", fill: accent)[")
                .append(esc(section.title())).append("]#v(6pt)\n")
                .append("    #text(size: 9.5pt)[").append(esc(section.body())).append("]\n")
                .append("  ]");
    }

    private static void appendSection(StringBuilder out, Section section) {
        if (section == null || blank(section.title()) || blank(section.body())) return;
        out.append("#text(size: 14pt, weight: \"bold\", fill: accent)[")
                .append(esc(section.title())).append("]#v(5pt)\n");
        out.append("#text(size: 10pt)[").append(esc(section.body())).append("]#v(12pt)\n");
    }

    private static boolean isAnimeStaff(Section section) {
        return section != null && section.title() != null && section.title().startsWith("主要职员");
    }

    private static boolean isAnimeCast(Section section) {
        return section != null && section.title() != null && section.title().startsWith("主要角色");
    }

    /** “琪琪手册”使用更醒目的双列功能入口，不与普通资料卡共用长列表版式。 */
    private static String buildManualTypst(Card card, List<PreparedItem> items, String accent) {
        String nl = System.lineSeparator();
        StringBuilder out = new StringBuilder("""
                #set page(width: 720pt, height: auto, margin: 0pt, fill: rgb("F2F4F7"))
                #set text(font: ("Microsoft YaHei", "Noto Sans CJK SC", "Source Han Sans SC", "Arial"), size: 11.5pt, fill: rgb("20252B"))
                #let accent = rgb("%s")
                #let muted = rgb("66707B")
                #let rule = 0.7pt + rgb("D4DAE1")
                #rect(width: 720pt, fill: white, stroke: rule, radius: 5pt, inset: 0pt)[
                #rect(width: 100%%, height: 9pt, fill: accent)
                #pad(24pt)[
                """.formatted(accent));
        out.append("#text(size: 29pt, weight: ").append(str("bold")).append(")[")
                .append(esc(card.title())).append("]").append(nl);
        if (!blank(card.subtitle())) {
            out.append("#h(10pt)#text(size: 11pt, weight: ").append(str("medium"))
                    .append(", fill: muted)[")
                    .append(esc(card.subtitle())).append("]").append(nl);
        }
        out.append("#v(9pt)#line(length: 100%, stroke: rule)#v(15pt)").append(nl);
        if (!blank(card.summary())) {
            out.append("#text(size: 12.5pt, weight: ").append(str("medium")).append(")[")
                    .append(esc(card.summary())).append("]#v(14pt)").append(nl);
        }
        if (!card.fields().isEmpty()) {
            out.append("#grid(columns: (92pt, 1fr), row-gutter: 7pt, column-gutter: 10pt,").append(nl);
            for (Field field : card.fields()) {
                out.append("[#text(size: 11pt, weight: ").append(str("bold"))
                        .append(", fill: muted)[").append(esc(field.name())).append("]],").append(nl);
                out.append("[#text(size: 11.5pt)[").append(esc(field.value())).append("]],").append(nl);
            }
            out.append(")#v(18pt)").append(nl);
        }

        out.append("#text(size: 16pt, weight: ").append(str("bold")).append(", fill: accent)[")
                .append(esc(blank(card.itemsTitle()) ? "功能与示例" : card.itemsTitle()))
                .append("]#v(9pt)").append(nl);
        out.append("#grid(columns: (1fr, 1fr), column-gutter: 12pt, row-gutter: 12pt,").append(nl);
        for (PreparedItem item : items) appendManualItem(out, item);
        out.append(")").append(nl);
        out.append("#v(14pt)#line(length: 100%, stroke: rule)#v(8pt)").append(nl);
        out.append("#align(right)[#text(size: 9pt, weight: ").append(str("bold"))
                .append(", fill: accent)[").append(AUTHOR).append("]]").append(nl);
        out.append("]").append(nl).append("]").append(nl);
        return out.toString();
    }

    private static void appendManualItem(StringBuilder out, PreparedItem item) {
        String nl = System.lineSeparator();
        boolean unavailable = !blank(item.meta()) && item.meta().contains("暂不可用");
        out.append("[#rect(width: 100%, height: 108pt, fill: rgb(").append(str("F8FAFC"))
                .append("), stroke: rule, radius: 4pt, inset: 12pt)[");
        out.append("#text(size: 13pt, weight: ").append(str("bold")).append(")[")
                .append(esc(item.title())).append("]");
        if (!blank(item.meta())) {
            out.append("#h(8pt)#text(size: 9.5pt, weight: ").append(str("bold")).append(", fill: ")
                    .append(unavailable ? "rgb(" + str("D64B4B") + ")" : "accent")
                    .append(")[").append(esc(item.meta())).append("]");
        }
        if (!blank(item.description())) {
            out.append("#v(7pt)#text(size: 10.5pt, fill: muted)[")
                    .append(esc(item.description())).append("]");
        }
        out.append("]],").append(nl);
    }
    private static void appendSummaryAndFields(StringBuilder out, Card card) {
        if (!blank(card.summary())) {
            out.append("#text(size: 11pt, weight: \"medium\")[")
                    .append(esc(card.summary())).append("]#v(10pt)\n");
        }
        if (!card.fields().isEmpty()) {
            out.append("#grid(columns: (88pt, 1fr), row-gutter: 5pt, column-gutter: 8pt,\n");
            for (Field field : card.fields()) {
                out.append("[#text(weight: \"bold\", fill: muted)[").append(esc(field.name())).append("]],\n");
                out.append("[#text[").append(esc(field.value())).append("]],\n");
            }
            out.append(")\n");
        }
    }

    private static void appendItem(StringBuilder out, PreparedItem item) {
        out.append("#line(length: 100%, stroke: rule)#v(7pt)\n");
        if (item.image() != null) {
            out.append("#grid(columns: (76pt, 1fr), gutter: 12pt, align: top,\n")
                    .append("image(").append(str(item.image())).append(", width: 76pt, height: 76pt, fit: \"cover\"),[");
        } else {
            out.append("#grid(columns: (7pt, 1fr), gutter: 10pt, align: top, rect(width: 7pt, height: 52pt, fill: accent, radius: 2pt),[");
        }
        out.append("#text(size: 11pt, weight: \"bold\")[").append(esc(item.title())).append("]");
        if (!blank(item.meta())) out.append("#h(7pt)#text(size: 8.5pt, fill: muted)[").append(esc(item.meta())).append("]");
        if (!blank(item.description())) out.append("#v(3pt)#text(size: 9pt)[").append(esc(item.description())).append("]");
        out.append("])#v(7pt)\n");
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

    private static String str(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean validColor(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{6}");
    }

    public record Card(String title, String subtitle, String summary, String imagePath, String accent,
                       List<Field> fields, List<Section> sections, String itemsTitle,
                       List<CardItem> items, String source) {
        public Card {
            fields = fields == null ? List.of() : List.copyOf(fields);
            sections = sections == null ? List.of() : List.copyOf(sections);
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record Field(String name, String value) {
    }

    public record Section(String title, String body) {
    }

    public record CardItem(String title, String meta, String description, String imagePath) {
    }

    private record PreparedItem(String title, String meta, String description, String image) {
    }
}
