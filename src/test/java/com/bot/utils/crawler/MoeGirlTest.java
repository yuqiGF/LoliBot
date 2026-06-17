package com.bot.utils.crawler;

import com.bot.utils.crawler.MoeGirlCrawler.InfoboxData;

/**
 * 简单测试类，用于直接测试萌娘爬虫功能
 */
public class MoeGirlTest {
    public static void main(String[] args) {
        System.out.println("开始测试萌娘百科爬虫...");

        try {
            InfoboxData data = MoeGirlCrawler.getInfo("初音未来");
            System.out.println("爬虫结果：");
            if (data != null) {
                System.out.println("标题: " + data.pageTitle);
                System.out.println("图片: " + data.imageUrl);
                System.out.println("字段数: " + data.fields.size());
                data.fields.forEach((k, v) -> System.out.println(k + ": " + v));
            } else {
                System.out.println("未找到结果");
            }
        } catch (Exception e) {
            System.out.println("爬虫出错：");
            e.printStackTrace();
        }
    }
}