package com.bot.utils.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bot.service.LightweightDashScopeService;
import com.bot.utils.common.RichCardRenderer.Card;
import com.bot.utils.common.RichCardRenderer.CardItem;
import com.bot.utils.common.RichCardRenderer.Field;
import com.bot.utils.common.RichCardRenderer.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用独立的通义千问调用将 AniList 卡片本地化为简体中文。
 *
 * <p>只把面向用户的文本交给模型；图片路径、来源 URL 和主题色始终从原卡片恢复，
 * 即使模型输出异常也不会破坏渲染或影响普通聊天记忆。</p>
 */
@Component
public class AnimeChineseTranslator {
    private static final Logger logger = LoggerFactory.getLogger(AnimeChineseTranslator.class);

    private final LightweightDashScopeService lightweightModel;

    public AnimeChineseTranslator(LightweightDashScopeService lightweightModel) {
        this.lightweightModel = lightweightModel;
    }

    public Card translate(Card original) {
        if (original == null) return null;
        try {
            String response = lightweightModel.translateAnime(buildPayload(original).toJSONString());
            JSONObject translated = parseObject(response);
            if (translated == null) return original;

            return new Card(
                    value(translated, "title", original.title()),
                    value(translated, "subtitle", original.subtitle()),
                    value(translated, "summary", original.summary()),
                    original.imagePath(),
                    original.accent(),
                    translatedFields(translated.getJSONArray("fields"), original.fields()),
                    translatedSections(translated.getJSONArray("sections"), original.sections()),
                    value(translated, "itemsTitle", original.itemsTitle()),
                    translatedItems(translated.getJSONArray("items"), original.items()),
                    original.source()
            );
        } catch (Exception e) {
            logger.warn("动漫中文翻译失败，保留 AniList 原始内容: {}", e.getMessage());
            return original;
        }
    }

    private static JSONObject buildPayload(Card card) {
        JSONObject root = new JSONObject();
        root.put("title", card.title());
        root.put("subtitle", card.subtitle());
        root.put("summary", card.summary());
        root.put("itemsTitle", card.itemsTitle());

        JSONArray fields = new JSONArray();
        for (Field field : card.fields()) {
            fields.add(new JSONObject().fluentPut("name", field.name()).fluentPut("value", field.value()));
        }
        root.put("fields", fields);

        JSONArray sections = new JSONArray();
        for (Section section : card.sections()) {
            sections.add(new JSONObject().fluentPut("title", section.title()).fluentPut("body", section.body()));
        }
        root.put("sections", sections);

        JSONArray items = new JSONArray();
        for (CardItem item : card.items()) {
            items.add(new JSONObject()
                    .fluentPut("title", item.title())
                    .fluentPut("meta", item.meta())
                    .fluentPut("description", item.description()));
        }
        root.put("items", items);
        return root;
    }

    private static JSONObject parseObject(String response) {
        if (response == null || response.isBlank()) return null;
        String text = response.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return JSON.parseObject(text.substring(start, end + 1));
    }

    private static List<Field> translatedFields(JSONArray values, List<Field> originals) {
        List<Field> result = new ArrayList<>();
        for (int i = 0; i < originals.size(); i++) {
            Field original = originals.get(i);
            JSONObject item = values != null && i < values.size() ? values.getJSONObject(i) : null;
            result.add(new Field(
                    value(item, "name", original.name()),
                    value(item, "value", original.value())
            ));
        }
        return result;
    }

    private static List<Section> translatedSections(JSONArray values, List<Section> originals) {
        List<Section> result = new ArrayList<>();
        for (int i = 0; i < originals.size(); i++) {
            Section original = originals.get(i);
            JSONObject item = values != null && i < values.size() ? values.getJSONObject(i) : null;
            result.add(new Section(
                    value(item, "title", original.title()),
                    value(item, "body", original.body())
            ));
        }
        return result;
    }

    private static List<CardItem> translatedItems(JSONArray values, List<CardItem> originals) {
        List<CardItem> result = new ArrayList<>();
        for (int i = 0; i < originals.size(); i++) {
            CardItem original = originals.get(i);
            JSONObject item = values != null && i < values.size() ? values.getJSONObject(i) : null;
            result.add(new CardItem(
                    value(item, "title", original.title()),
                    value(item, "meta", original.meta()),
                    value(item, "description", original.description()),
                    original.imagePath()
            ));
        }
        return result;
    }

    private static String value(JSONObject object, String key, String fallback) {
        if (object == null) return fallback;
        String value = object.getString(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}