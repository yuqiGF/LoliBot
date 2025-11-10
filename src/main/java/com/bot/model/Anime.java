package com.bot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 番剧实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Anime {
    private String cnName;     // 中文名
    private String score;      // 评分
    private String imageUrl;   // 封面图URL
}

