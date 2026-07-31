package com.bot.utils.crawler;

import com.bot.utils.common.TypstRenderUtils;
import com.bot.utils.crawler.PRTSCrawler.CardType;
import com.bot.utils.crawler.PRTSCrawler.PRTSData;
import com.bot.utils.crawler.PRTSCrawler.PRTSImage;
import com.bot.utils.crawler.PRTSCrawler.PRTSRow;
import com.bot.utils.crawler.PRTSCrawler.PRTSSkill;
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
 * PRTS 查询结果卡片渲染器。
 *
 * <p>这里尽量复刻 PRTS/Mooncell 的网页表格感：浅色背景、灰色表头、细边框、合并单元格。
 * 爬虫负责结构化数据，本类只负责把数据拼成 Typst 图片。</p>
 */
public class PRTSCardRenderer {

    private static final Logger logger = LoggerFactory.getLogger(PRTSCardRenderer.class);

    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(28);
    private static final int MAX_RELATED_ON_CARD = 30;
    private static final int MAX_AVATARS_ON_CARD = 30;
    private static final String AUTHOR_LABEL = "宇崎崎捏";

    private PRTSCardRenderer() {
    }

    public static File render(PRTSData data, String typstPath, String fontPath, String cacheKey) {
        if (data == null) {
            return null;
        }
        try {
            Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"), "lolibot_prts");
            Files.createDirectories(tmpDir);
            String id = safeId((data.pageTitle == null ? "" : data.pageTitle) + "_" + cacheKey);
            String fileStem = "prts_card_" + id;
            String mainImage = prepareImage(data.imagePath, tmpDir, "main_" + id);
            List<CardImage> images = prepareImages(data.images, tmpDir, id);
            List<CardSkill> skills = prepareSkills(data.skills, tmpDir, id);
            return TypstRenderUtils.compileToPng(
                    typstPath,
                    fontPath,
                    tmpDir,
                    fileStem,
                    buildTypst(data, mainImage, images, skills),
                    RENDER_TIMEOUT
            );
        } catch (Exception e) {
            logger.warn("PRTS 卡片渲染异常", e);
            return null;
        }
    }

    public static File renderHelp(String typstPath, String fontPath, String cacheKey) {
        try {
            Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"), "lolibot_prts");
            Files.createDirectories(tmpDir);
            String fileStem = "prts_help_" + safeId(cacheKey);
            return TypstRenderUtils.compileToPng(
                    typstPath,
                    fontPath,
                    tmpDir,
                    fileStem,
                    buildHelpTypst(),
                    RENDER_TIMEOUT
            );
        } catch (Exception e) {
            logger.warn("PRTS 帮助卡片渲染异常", e);
            return null;
        }
    }

    private static String prepareImage(String imagePath, Path tmpDir, String stem) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        try {
            Path source = Path.of(imagePath);
            if (!Files.exists(source)) {
                return null;
            }
            String fileName = stem + extension(source.getFileName().toString());
            Files.copy(source, tmpDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (Exception e) {
            logger.warn("PRTS 图片复制失败: {}", e.getMessage());
            return null;
        }
    }

    private static List<CardImage> prepareImages(List<PRTSImage> images, Path tmpDir, String id) {
        List<CardImage> result = new ArrayList<>();
        if (images == null) {
            return result;
        }
        int index = 0;
        for (PRTSImage image : images) {
            if (index >= MAX_AVATARS_ON_CARD) {
                break;
            }
            String local = prepareImage(image.imagePath(), tmpDir, "avatar_" + id + "_" + index);
            if (local != null) {
                result.add(new CardImage(image.name(), local));
                index++;
            }
        }
        return result;
    }

    private static List<CardSkill> prepareSkills(List<PRTSSkill> skills, Path tmpDir, String id) {
        List<CardSkill> result = new ArrayList<>();
        if (skills == null) {
            return result;
        }
        int index = 0;
        for (PRTSSkill skill : skills) {
            String local = prepareImage(skill.imagePath(), tmpDir, "skill_" + id + "_" + index);
            result.add(new CardSkill(skill.name(), skill.meta(), skill.description(), local));
            index++;
        }
        return result;
    }

    private static String extension(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        for (String ext : List.of(".png", ".jpg", ".jpeg", ".webp")) {
            if (lower.endsWith(ext)) {
                return ext.equals(".jpeg") ? ".jpg" : ext;
            }
        }
        return ".png";
    }

    private static String buildTypst(PRTSData data, String mainImage, List<CardImage> images, List<CardSkill> skills) {
        String width = data.cardType == CardType.OPERATOR || data.modeName.contains("关卡") ? "720pt" : "620pt";
        if (data.cardType == CardType.RECRUIT) {
            width = "600pt";
        }
        StringBuilder sb = new StringBuilder();
        appendPreamble(sb, width);
        sb.append("#rect(width: W, radius: 3pt, fill: PAGE, stroke: BORDER, inset: 0pt)[\n");
        appendWikiTitle(sb, data.pageTitle, data.modeName);
        if (data.cardType == CardType.OPERATOR) {
            appendOperator(sb, data, mainImage, skills);
        } else if (data.cardType == CardType.ENEMY) {
            appendEnemy(sb, data, mainImage, skills);
        } else if (data.cardType == CardType.RECRUIT) {
            appendRecruit(sb, data, images);
        } else if (data.modeName.contains("关卡")) {
            appendStage(sb, data, mainImage, images);
        } else if (data.modeName.contains("道具")) {
            appendItem(sb, data, mainImage);
        } else if (data.modeName.contains("时装")) {
            appendOutfit(sb, data, mainImage);
        } else {
            appendGeneric(sb, data, mainImage);
        }
        appendFooter(sb, "PRTS Wiki · " + nullToBlank(data.sourceUrl));
        sb.append("]\n");
        return sb.toString();
    }

    private static String buildHelpTypst() {
        String[][] modes = {
                {"prts 羽毛笔", "干员页：立绘、基础信息、完整属性、天赋与技能图标。"},
                {"prts 源石虫", "敌人页：头像/模型、描述、分类与等级属性表。"},
                {"prts 转质盐组", "道具页：图标、用途、描述、掉落与合成信息。"},
                {"prts 1-7", "关卡页：基础信息、地图预览、敌方情报头像。"},
                {"prts 主线第13章", "章节列表：先展示该章全部关卡，再继续查详情。"},
                {"prts 夏卉 FA210", "时装页：时装图、画师、获取途径、时装组和描述。"},
                {"prts 高级资深干员 近卫", "公招筛选：直接输入任意 tag 组合返回干员头像。"},
                {"prts 资深干员 快速复活", "多 tag 公招筛选：支持职业、位置、资质和词条组合。"}
        };
        StringBuilder sb = new StringBuilder();
        appendPreamble(sb, "640pt");
        sb.append("#rect(width: W, radius: 3pt, fill: PAGE, stroke: BORDER, inset: 0pt)[\n");
        appendWikiTitle(sb, "PRTS 查询帮助", "使用帮助");
        appendNotice(sb, "直接输入 prts 查看帮助；查询无需二级指令，输入名称、关卡或公招 tag 即可。");
        appendCommandTable(sb, modes);
        appendFooter(sb, "技能/敌人/道具/关卡/时装均按网页表格风格生成图片 · " + AUTHOR_LABEL);
        sb.append("]\n");
        return sb.toString();
    }

    private static void appendPreamble(StringBuilder sb, String width) {
        sb.append("#set page(width: auto, height: auto, margin: 0pt)\n");
        sb.append("#set text(font: (\"Microsoft YaHei\", \"Noto Sans SC\", \"Noto Sans CJK SC\", \"Source Han Sans SC\", \"SimHei\", \"Arial\", \"Segoe UI\"), size: 9pt)\n");
        sb.append("#let W = ").append(width).append("\n");
        sb.append("#let PAGE = rgb(\"FAFAF6\")\n");
        sb.append("#let HEAD = rgb(\"565656\")\n");
        sb.append("#let SUB = rgb(\"E5E7EA\")\n");
        sb.append("#let CELL = rgb(\"F6F7F8\")\n");
        sb.append("#let CYAN = rgb(\"12A9DF\")\n");
        sb.append("#let RED = rgb(\"C91B27\")\n");
        sb.append("#let BORDER = 0.55pt + rgb(\"AAB0B8\")\n");
        sb.append("#let BLUE = rgb(\"0057B8\")\n");
    }

    private static void appendWikiTitle(StringBuilder sb, String title, String mode) {
        sb.append("  #block(width: W, inset: (x: 12pt, y: 10pt))[\n");
        sb.append("    #text(size: 17pt, weight: \"bold\")[").append(escapeMarkup(nullToBlank(title))).append("]");
        sb.append("#h(8pt)#text(size: 8pt, fill: rgb(\"777777\"))[").append(escapeMarkup(nullToBlank(mode))).append("]\n");
        sb.append("    #line(length: 100%, stroke: 0.65pt + rgb(\"8F969F\"))\n");
        sb.append("  ]\n");
    }

    private static void appendOperator(StringBuilder sb, PRTSData data, String mainImage, List<CardSkill> skills) {
        appendOperatorHero(sb, mainImage);
        List<PRTSRow> basic = new ArrayList<>();
        List<PRTSRow> attrs = new ArrayList<>();
        for (PRTSRow row : data.rows) {
            if (isBasicInfo(row.label())) {
                basic.add(row);
            } else {
                attrs.add(row);
            }
        }
        appendPairTable(sb, "基础信息", basic, "HEAD");
        appendPairTable(sb, "属性", attrs, "HEAD");
        appendTalentTable(sb, skills);
        appendSkillTable(sb, skills);
    }

    private static void appendOperatorHero(StringBuilder sb, String mainImage) {
        if (mainImage == null || mainImage.isBlank()) {
            return;
        }
        sb.append("  #table(columns: (1fr), stroke: BORDER, inset: 0pt,\n");
        sb.append("    table.cell(fill: HEAD, inset: 5pt)[#align(center)[#text(fill: white, weight: \"bold\")[干员信息]]],\n");
        sb.append("    table.cell(fill: CELL, inset: 0pt)[#align(center)[#image(").append(typstString(mainImage)).append(", height: 300pt)]],\n");
        sb.append("  )\n");
    }

    private static void appendEnemy(StringBuilder sb, PRTSData data, String mainImage, List<CardSkill> skills) {
        appendImageSummaryTable(sb, data.pageTitle, data.summary, mainImage, "RED");
        appendPairTable(sb, "属性", data.rows, "HEAD");
        appendTalentTable(sb, skills);
    }

    private static void appendStage(StringBuilder sb, PRTSData data, String mainImage, List<CardImage> enemies) {
        appendPairTable(sb, "关卡信息", data.rows, "HEAD");
        if (mainImage != null && !mainImage.isBlank()) {
            sb.append("  #table(columns: (1fr), stroke: BORDER, inset: 0pt,\n");
            sb.append("    table.cell(fill: SUB, inset: 5pt)[#align(center)[#text(weight: \"bold\")[地图]]],\n");
            sb.append("    table.cell(fill: CELL, inset: 4pt)[#align(center)[#image(").append(typstString(mainImage)).append(", width: 690pt)]],\n");
            sb.append("  )\n");
        }
        appendImageGrid(sb, enemies, "敌方情报");
        appendRelated(sb, data);
    }

    private static void appendItem(StringBuilder sb, PRTSData data, String mainImage) {
        appendImageSummaryTable(sb, data.pageTitle, data.summary, mainImage, "CYAN");
        appendPairTable(sb, "基础信息", data.rows, "CYAN");
        appendRelated(sb, data);
    }

    private static void appendOutfit(StringBuilder sb, PRTSData data, String mainImage) {
        appendImageSummaryTable(sb, data.pageTitle, data.summary, mainImage, "HEAD");
        appendPairTable(sb, "时装信息", data.rows, "HEAD");
    }

    private static void appendGeneric(StringBuilder sb, PRTSData data, String mainImage) {
        appendImageSummaryTable(sb, data.pageTitle, data.summary, mainImage, "HEAD");
        appendPairTable(sb, "基础信息", data.rows, "HEAD");
        appendRelated(sb, data);
    }

    private static void appendRecruit(StringBuilder sb, PRTSData data, List<CardImage> images) {
        appendNotice(sb, data.summary);
        appendPairTable(sb, "筛选条件", data.rows, "HEAD");
        appendImageGrid(sb, images, "匹配干员");
    }

    private static void appendImageSummaryTable(StringBuilder sb, String title, String summary, String image, String headColor) {
        sb.append("  #table(columns: (185pt, 1fr), stroke: BORDER, inset: 0pt,\n");
        sb.append("    table.cell(colspan: 2, fill: ").append(headColor).append(", inset: 5pt)[#align(center)[#text(fill: white, weight: \"bold\")[").append(escapeMarkup(title)).append("]]],\n");
        if (image != null && !image.isBlank()) {
            sb.append("    table.cell(rowspan: 2, fill: CELL, inset: 8pt)[#align(center)[#image(").append(typstString(image)).append(", width: 150pt)]],\n");
        } else {
            sb.append("    table.cell(rowspan: 2, fill: CELL, inset: 8pt)[#align(center)[#text(fill: rgb(\"888888\"))[NO IMAGE]]],\n");
        }
        sb.append("    table.cell(fill: SUB, inset: 5pt)[#align(center)[#text(weight: \"bold\")[描述]]],\n");
        sb.append("    table.cell(fill: CELL, inset: 8pt)[#text(size: 9pt)[").append(escapeMarkup(nullToBlank(summary))).append("]],\n");
        sb.append("  )\n");
    }

    private static void appendPairTable(StringBuilder sb, String title, List<PRTSRow> rows, String headColor) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        sb.append("  #table(columns: (105pt, 1fr, 105pt, 1fr), stroke: BORDER, inset: (x: 5pt, y: 4pt),\n");
        sb.append("    table.cell(colspan: 4, fill: ").append(headColor).append(", inset: 5pt)[#align(center)[#text(fill: white, weight: \"bold\")[").append(escapeMarkup(title)).append("]]],\n");
        for (int i = 0; i < rows.size(); i += 2) {
            appendLabelCell(sb, rows.get(i).label());
            appendValueCell(sb, rows.get(i).value());
            if (i + 1 < rows.size()) {
                appendLabelCell(sb, rows.get(i + 1).label());
                appendValueCell(sb, rows.get(i + 1).value());
            } else {
                appendLabelCell(sb, "");
                appendValueCell(sb, "");
            }
        }
        sb.append("  )\n");
    }

    private static void appendTalentTable(StringBuilder sb, List<CardSkill> skills) {
        List<CardSkill> talents = new ArrayList<>();
        for (CardSkill skill : skills) {
            if (nullToBlank(skill.meta()).startsWith("天赋")) {
                talents.add(skill);
            }
        }
        if (talents.isEmpty()) {
            return;
        }
        sb.append("  #table(columns: (115pt, 120pt, 1fr), stroke: BORDER, inset: (x: 5pt, y: 4pt),\n");
        sb.append("    table.cell(colspan: 3, fill: HEAD, inset: 5pt)[#align(center)[#text(fill: white, weight: \"bold\")[天赋]]],\n");
        for (CardSkill talent : talents) {
            appendLabelCell(sb, talent.name());
            appendValueCell(sb, nullToBlank(talent.meta()).replace("天赋 · ", ""));
            appendValueCell(sb, talent.description());
        }
        sb.append("  )\n");
    }

    private static void appendSkillTable(StringBuilder sb, List<CardSkill> skills) {
        List<CardSkill> realSkills = new ArrayList<>();
        for (CardSkill skill : skills) {
            if (!nullToBlank(skill.meta()).startsWith("天赋")) {
                realSkills.add(skill);
            }
        }
        if (realSkills.isEmpty()) {
            return;
        }
        sb.append("  #table(columns: (72pt, 120pt, 1fr), stroke: BORDER, inset: (x: 5pt, y: 5pt),\n");
        sb.append("    table.cell(colspan: 3, fill: HEAD, inset: 5pt)[#align(center)[#text(fill: white, weight: \"bold\")[技能]]],\n");
        for (CardSkill skill : realSkills) {
            if (skill.localImage() != null) {
                sb.append("    table.cell(fill: CELL, inset: 5pt)[#align(center)[#image(").append(typstString(skill.localImage())).append(", width: 42pt)]],\n");
            } else {
                sb.append("    table.cell(fill: CELL, inset: 5pt)[#align(center)[#text(fill: rgb(\"888888\"))[技能]]],\n");
            }
            appendLabelCell(sb, skill.name() + "\n" + nullToBlank(skill.meta()));
            appendValueCell(sb, skill.description());
        }
        sb.append("  )\n");
    }

    private static void appendImageGrid(StringBuilder sb, List<CardImage> images, String title) {
        if (images == null || images.isEmpty()) {
            return;
        }
        sb.append("  #table(columns: (1fr), stroke: BORDER, inset: 0pt,\n");
        sb.append("    table.cell(fill: HEAD, inset: 5pt)[#align(center)[#text(fill: white, weight: \"bold\")[").append(escapeMarkup(title)).append("]]],\n");
        sb.append("    table.cell(fill: CELL, inset: 8pt)[#grid(columns: (1fr, 1fr, 1fr, 1fr, 1fr, 1fr), gutter: 7pt,\n");
        for (CardImage image : images) {
            sb.append("      [#align(center)[#image(").append(typstString(image.localName())).append(", width: 42pt)#linebreak()#text(size: 7pt, fill: BLUE)[").append(escapeMarkup(image.name())).append("]]],\n");
        }
        sb.append("    )],\n");
        sb.append("  )\n");
    }

    private static void appendCommandTable(StringBuilder sb, String[][] modes) {
        sb.append("  #table(columns: (170pt, 1fr), stroke: BORDER, inset: (x: 8pt, y: 5pt),\n");
        sb.append("    table.cell(colspan: 2, fill: HEAD, inset: 5pt)[#align(center)[#text(fill: white, weight: \"bold\")[指令示例]]],\n");
        for (String[] mode : modes) {
            appendLabelCell(sb, mode[0]);
            appendValueCell(sb, mode[1]);
        }
        sb.append("  )\n");
    }

    private static void appendNotice(StringBuilder sb, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        sb.append("  #table(columns: (1fr), stroke: BORDER, inset: 6pt,\n");
        sb.append("    table.cell(fill: CELL)[#text(size: 9pt)[").append(escapeMarkup(text)).append("]],\n");
        sb.append("  )\n");
    }

    private static void appendRelated(StringBuilder sb, PRTSData data) {
        List<String> relatedTitles = data.relatedTitles;
        if (relatedTitles == null || relatedTitles.isEmpty()) {
            return;
        }
        int limit = Math.min(MAX_RELATED_ON_CARD, relatedTitles.size());
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                joined.append(" · ");
            }
            joined.append(relatedTitles.get(i));
        }
        appendNotice(sb, (data.listOverview ? "可查询条目：" : "相关条目：") + joined);
    }

    private static void appendFooter(StringBuilder sb, String sourceText) {
        sb.append("  #table(columns: (1fr, 70pt), stroke: BORDER, inset: (x: 7pt, y: 4pt),\n");
        sb.append("    table.cell(fill: SUB)[#text(size: 7pt, fill: rgb(\"666666\"))[").append(escapeMarkup(sourceText)).append("]],\n");
        sb.append("    table.cell(fill: SUB)[#align(center)[#text(size: 7pt, fill: rgb(\"666666\"))[").append(escapeMarkup(AUTHOR_LABEL)).append("]]],\n");
        sb.append("  )\n");
    }

    private static void appendLabelCell(StringBuilder sb, String text) {
        sb.append("    table.cell(fill: SUB, inset: (x: 5pt, y: 4pt))[#align(center)[#text(weight: \"bold\")[")
                .append(escapeMarkup(text)).append("]]],\n");
    }

    private static void appendValueCell(StringBuilder sb, String text) {
        sb.append("    table.cell(fill: CELL, inset: (x: 6pt, y: 4pt))[#align(center)[#text[")
                .append(escapeMarkup(text)).append("]]],\n");
    }

    private static boolean isBasicInfo(String label) {
        return List.of("英文名", "星级", "职业", "位置/标签", "阵营", "画师").contains(label);
    }

    private static String typstString(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private static String escapeMarkup(String value) {
        String text = nullToBlank(value).replace('\r', ' ').replace('\n', ' ').trim();
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\' || ch == '[' || ch == ']' || ch == '#'
                    || ch == '$' || ch == '%' || ch == '*' || ch == '_'
                    || ch == 96) {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String safeId(String value) {
        return Integer.toHexString(value == null ? 0 : value.hashCode());
    }

    private record CardImage(String name, String localName) {
    }

    private record CardSkill(String name, String meta, String description, String localImage) {
    }
}