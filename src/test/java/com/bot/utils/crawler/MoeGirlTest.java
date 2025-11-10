package com.bot.utils.crawler;

/**
 * 简单测试类，用于直接测试萌娘爬虫功能
 */
public class MoeGirlTest {
    public static void main(String[] args) {
        System.out.println("开始测试萌娘百科爬虫...");
        
        try {
            String result = MoeGirlCrawler.getInfo("初音未来");
            System.out.println("爬虫结果：");
            System.out.println(result);
        } catch (Exception e) {
            System.out.println("爬虫出错：");
            e.printStackTrace();
        }
    }
}