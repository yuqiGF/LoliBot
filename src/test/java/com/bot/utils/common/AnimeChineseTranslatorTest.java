package com.bot.utils.common;

import com.bot.service.LightweightDashScopeService;
import com.bot.utils.common.RichCardRenderer.Card;
import com.bot.utils.common.RichCardRenderer.CardItem;
import com.bot.utils.common.RichCardRenderer.Field;
import com.bot.utils.common.RichCardRenderer.Section;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnimeChineseTranslatorTest {

    @Test
    void translatesReaderTextButPreservesTechnicalValues() {
        LightweightDashScopeService model = mock(LightweightDashScopeService.class);
        when(model.translateAnime(anyString())).thenReturn("""
                {
                  "title":"葬送的芙莉莲",
                  "subtitle":"动漫资料",
                  "summary":"勇者一行打倒魔王之后的故事。",
                  "itemsTitle":"更新日程",
                  "fields":[{"name":"状态","value":"已完结"}],
                  "sections":[{"title":"主要角色 / 声优","body":"芙莉莲：种崎敦美"}],
                  "items":[{"title":"中文标题","meta":"第 1 集","description":"周五更新"}]
                }
                """);

        Card original = new Card(
                "葬送のフリーレン", "ANIME", "English summary",
                "C:/image.jpg", "E4576B",
                List.of(new Field("Status", "FINISHED")),
                List.of(new Section("Cast", "Frieren: Atsumi Tanezaki")),
                "Schedule",
                List.of(new CardItem("Original", "Episode 1", "Friday", "C:/item.jpg")),
                "https://anilist.co/anime/154587"
        );

        Card translated = new AnimeChineseTranslator(model).translate(original);
        assertEquals("葬送的芙莉莲", translated.title());
        assertEquals("勇者一行打倒魔王之后的故事。", translated.summary());
        assertEquals("芙莉莲：种崎敦美", translated.sections().getFirst().body());
        assertEquals("C:/image.jpg", translated.imagePath());
        assertEquals("C:/item.jpg", translated.items().getFirst().imagePath());
        assertEquals(original.source(), translated.source());
    }
}