package com.bot.utils.crawler;

import com.bot.utils.crawler.MoeGirlCrawler.InfoboxData;
import com.bot.utils.crawler.MoeGirlCrawler.InfoboxRow;
import com.bot.utils.common.TypstRenderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class MoeGirlCardRenderer {

    private static final Logger logger = LoggerFactory.getLogger(MoeGirlCardRenderer.class);

    private static final String DEFAULT_SECTION_FILL = "DEEDE0";
    private static final String RELATION_SECTION_FILL = "E0FFFF";
    private static final String LINK_BLUE = "0645AD";
    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(25);

    private MoeGirlCardRenderer() {
    }

    public static File render(InfoboxData data, String typstPath, String cacheKey) {
        return render(data, typstPath, null, cacheKey);
    }

    public static File render(InfoboxData data, String typstPath, String fontPath, String cacheKey) {
        if (data == null || data.isDisambiguation) return null;

        try {
            Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"), "lolibot_moegirl");
            Files.createDirectories(tmpDir);

            String id = safeId((data.pageTitle == null ? "" : data.pageTitle) + "_" + cacheKey);
            String fileStem = "moegirl_card_" + id;
            String localImageName = prepareImage(data, tmpDir, id);

            String typstCode = buildTypst(data, localImageName);
            return TypstRenderUtils.compileToPng(
                    typstPath,
                    fontPath,
                    tmpDir,
                    fileStem,
                    typstCode,
                    RENDER_TIMEOUT
            );
        } catch (Exception e) {
            logger.warn("萌百卡片渲染异常", e);
            return null;
        }
    }

    private static String prepareImage(InfoboxData data, Path tmpDir, String id) {
        if (data.imagePath == null || data.imagePath.isBlank()) return null;

        try {
            Path source = Path.of(data.imagePath);
            if (!Files.exists(source)) return null;

            String ext = extension(source.getFileName().toString());
            String fileName = "moegirl_card_" + id + ext;
            Files.copy(source, tmpDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (Exception e) {
            logger.warn("萌百卡片复制封面失败: {}", e.getMessage());
            return null;
        }
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String ext = fileName.substring(dot).toLowerCase();
            if (ext.length() <= 6) return ext;
        }
        return ".jpg";
    }

    private static String buildTypst(InfoboxData data, String localImageName) {
        StringBuilder sb = new StringBuilder();
        sb.append("#set page(width: auto, height: auto, margin: 0pt)\n");
        sb.append("#set text(font: (\"Microsoft YaHei\", \"SimHei\", \"SimSun\", \"Noto Sans SC\", \"Noto Sans CJK SC\", \"Source Han Sans SC\", \"Arial\", \"Segoe UI\"), size: 8.2pt)\n");
        sb.append("#let W = 280pt\n");
        sb.append("#let LW = 66pt\n");
        sb.append("#let VW = W - LW\n");
        sb.append("#let BLUE = rgb(\"").append(LINK_BLUE).append("\")\n");
        sb.append("#let BORDER = 0.7pt + rgb(\"A8A8A8\")\n");
        sb.append("#let SEP = 0.35pt + rgb(\"E6E6E6\")\n");
        sb.append("#rect(width: W, radius: 4pt, stroke: BORDER, fill: white, inset: 0pt)[\n");
        sb.append("  #table(\n");
        sb.append("    columns: (LW, VW),\n");
        sb.append("    stroke: none,\n");
        sb.append("    inset: 0pt,\n");

        appendImage(sb, localImageName);
        appendCaptions(sb, data);

        List<InfoboxRow> rows = normalizedRows(data);
        if (rows.isEmpty() && data.pageTitle != null && !data.pageTitle.isBlank()) {
            rows.add(InfoboxRow.section(data.pageTitle, RELATION_SECTION_FILL));
        }

        for (InfoboxRow row : rows) {
            appendRow(sb, row);
        }

        sb.append("  )\n");
        sb.append("]\n");
        return sb.toString();
    }

    private static void appendImage(StringBuilder sb, String localImageName) {
        if (localImageName == null || localImageName.isBlank()) return;
        sb.append("    table.cell(colspan: 2, inset: 0pt)[#image(")
                .append(typstString(localImageName))
                .append(", width: W)],\n");
    }

    private static void appendCaptions(StringBuilder sb, InfoboxData data) {
        for (String line : data.captionLines) {
            if (line == null || line.isBlank()) continue;
            sb.append("    table.cell(colspan: 2, inset: (x: 2pt, y: 0.8pt), align: center, stroke: (bottom: SEP))[")
                    .append(markupText(line, LINK_BLUE, false, "7.6pt"))
                    .append("],\n");
        }
    }

    private static List<InfoboxRow> normalizedRows(InfoboxData data) {
        if (data.cardRows != null && !data.cardRows.isEmpty()) {
            return new ArrayList<>(data.cardRows);
        }

        List<InfoboxRow> rows = new ArrayList<>();
        if (data.fields != null) {
            data.fields.forEach((key, value) -> rows.add(InfoboxRow.field(key, value, false)));
        }
        return rows;
    }

    private static void appendRow(StringBuilder sb, InfoboxRow row) {
        if (row == null || row.type == null) return;

        switch (row.type) {
            case SECTION -> {
                String fill = row.backgroundColor == null ? DEFAULT_SECTION_FILL : row.backgroundColor;
                String label = row.label == null ? "" : row.label;
                sb.append("    table.cell(colspan: 2, fill: rgb(\"")
                        .append(fill)
                        .append("\"), inset: (x: 2pt, y: 1.2pt), align: center, stroke: (top: SEP, bottom: SEP))[")
                        .append(markupText(label, "000000", true, "8pt"))
                        .append("],\n");
            }
            case FIELD -> {
                String label = row.label == null ? "" : row.label;
                String value = row.value == null ? "" : row.value;
                String valueFill = row.linkLike ? LINK_BLUE : "000000";

                sb.append("    table.cell(inset: (x: 3pt, y: 1.4pt), align: right + top)[")
                        .append(markupText(label, "000000", true, "8pt"))
                        .append("],\n");
                sb.append("    table.cell(inset: (x: 4pt, y: 1.4pt), align: center + top)[")
                        .append(markupText(value, valueFill, false, "8pt"))
                        .append("],\n");
            }
            case FULL_WIDTH -> {
                String fill = row.backgroundColor == null ? null : row.backgroundColor;
                String valueFill = row.linkLike ? LINK_BLUE : "000000";
                sb.append("    table.cell(colspan: 2");
                if (fill != null) sb.append(", fill: rgb(\"").append(fill).append("\")");
                sb.append(", inset: (x: 5pt, y: 2pt), align: center + top, stroke: (top: SEP))[")
                        .append(markupText(row.value == null ? "" : row.value, valueFill, false, "8pt"))
                        .append("],\n");
            }
        }
    }

    private static String markupText(String value, String fill, boolean bold, String size) {
        StringBuilder sb = new StringBuilder();
        sb.append("#text(fill: rgb(\"").append(fill).append("\"), size: ").append(size);
        if (bold) sb.append(", weight: \"bold\"");
        sb.append(")[");
        sb.append(escapeMarkup(value));
        sb.append("]");
        return sb.toString();
    }

    private static String escapeMarkup(String value) {
        String text = value == null ? "" : value.replace("\r\n", " ").replace('\n', ' ').trim();
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\' || ch == '[' || ch == ']' || ch == '#'
                    || ch == '$' || ch == '%' || ch == '*' || ch == '_'
                    || ch == '`') {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    private static String typstString(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private static String safeId(String value) {
        return Integer.toHexString(value == null ? 0 : value.hashCode());
    }
}
