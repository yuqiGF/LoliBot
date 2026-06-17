package com.bot.utils;

import com.bot.utils.crawler.BangumiCrawler;
import com.bot.utils.crawler.MoeGirlCrawler;
import com.bot.utils.crawler.MoeGirlCrawler.InfoboxData;
import org.junit.jupiter.api.Test;

/**
 * 爬虫测试类
 * 用于验证爬虫优化效果
 */
public class CrawlerTest {

    /**
     * 测试 BangumiCrawler - 获取今日新番
     */
    @Test
    public void testGetTodayAnime() {
        System.out.println("========== 测试 BangumiCrawler ==========");
        long startTime = System.currentTimeMillis();
        
        String result = BangumiCrawler.getTodayAnime();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("\n结果：");
        System.out.println(result);
        System.out.println("\n耗时：" + duration + " ms");
        System.out.println("=====================================\n");
    }

    /**
     * 测试 BangumiCrawler - 搜索角色
     */
    @Test
    public void testSearchAnimeCharacter() {
        System.out.println("========== 测试 BangumiCrawler - 搜索角色 ==========");
        long startTime = System.currentTimeMillis();
        
        String characterName = "初音未来"; // 测试角色名
        String result = BangumiCrawler.searchCharacter(characterName);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("\n结果：");
        System.out.println(result);
        System.out.println("\n耗时：" + duration + " ms");
        System.out.println("=====================================\n");
    }

    /**
     * 测试 MoeGirlCrawler - 获取角色信息
     */
    @Test
    public void testGetCharacterInfo() {
        System.out.println("========== 测试 MoeGirlCrawler ==========");
        long startTime = System.currentTimeMillis();
        
        String characterName = "初音"; // 测试角色名
        InfoboxData data = MoeGirlCrawler.getInfo(characterName);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("\n结果：");
        if (data != null) {
            System.out.println("标题: " + data.pageTitle);
            System.out.println("图片: " + data.imageUrl);
            System.out.println("字段数: " + data.fields.size());
            data.fields.forEach((k, v) -> System.out.println(k + ": " + v));
        } else {
            System.out.println("未找到结果");
        }
        System.out.println("\n耗时：" + duration + " ms");
        System.out.println("=====================================\n");
    }
    
    /**
     * 测试智能搜索功能 - 测试相似度匹配
     */
    @Test
    public void testSmartSearch() {
        System.out.println("========== 测试智能搜索（相似度匹配）==========");
        
        String[] testCases = {
            "初音未来",      // 完全匹配
            "初音",          // 部分匹配
            "miku",          // 英文名
            "雷姆",          // Re:Zero角色
            "艾米莉亚"       // Re:Zero角色
        };
        
        for (String characterName : testCases) {
            System.out.println("\n--- 测试: " + characterName + " ---");
            long startTime = System.currentTimeMillis();
            
            InfoboxData data = MoeGirlCrawler.getInfo(characterName);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            System.out.println("耗时：" + duration + " ms");
            if (data != null) {
                System.out.println("标题：" + data.pageTitle + ", 字段数：" + data.fields.size());
            } else {
                System.out.println("结果：未找到");
            }
        }
        
        System.out.println("\n=====================================\n");
    }
    
    /**
     * 测试不同类型的条目
     */
    @Test
    public void testDifferentEntryTypes() {
        System.out.println("========== 测试不同类型条目 ==========");
        
        String[] testCases = {
            "初音未来",          // 角色
            "龙女仆",            // 动漫
            "原神",              // 游戏
            "awsl"              // 网络梗
        };
        
        for (String term : testCases) {
            System.out.println("\n━━━━━━ " + term + " ━━━━━━");
            long startTime = System.currentTimeMillis();
            
            InfoboxData data = MoeGirlCrawler.getInfo(term);

            long endTime = System.currentTimeMillis();

            if (data != null) {
                System.out.println("标题: " + data.pageTitle + ", 字段数: " + data.fields.size());
            } else {
                System.out.println("未找到结果");
            }
            System.out.println("\n耗时：" + (endTime - startTime) + " ms");
        }
        
        System.out.println("\n=====================================\n");
    }

    /**
     * 测试多次连续请求（验证连接池效果）
     */
    @Test
    public void testMultipleRequests() {
        System.out.println("========== 测试连接池效果（连续5次请求）==========");
        
        String[] characters = {"初音未来", "雷姆", "蕾姆", "涂山苏苏", "夜斗"};
        long totalTime = 0;
        
        for (int i = 0; i < characters.length; i++) {
            System.out.println("\n--- 第 " + (i + 1) + " 次请求: " + characters[i] + " ---");
            long startTime = System.currentTimeMillis();
            
            String result = BangumiCrawler.searchCharacter(characters[i]);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            totalTime += duration;
            
            System.out.println("耗时：" + duration + " ms");
            System.out.println("结果摘要：" + 
                (result.length() > 100 ? result.substring(0, 100) + "..." : result));
        }
        
        System.out.println("\n总耗时：" + totalTime + " ms");
        System.out.println("平均耗时：" + (totalTime / characters.length) + " ms");
        System.out.println("=====================================\n");
    }

    /**
     * 压力测试 - 测试重试机制
     * 注意：这个测试会故意访问可能不存在的条目来触发重试
     */
    @Test
    public void testRetryMechanism() {
        System.out.println("========== 测试重试机制 ==========");
        long startTime = System.currentTimeMillis();
        
        // 使用一个可能不存在的角色名来测试重试逻辑
        String nonExistentCharacter = "这是一个完全不存在的角色名12345ABCDE";
            InfoboxData data = MoeGirlCrawler.getInfo(nonExistentCharacter);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("\n结果：");
        System.out.println(data == null ? "未找到（预期行为）" : "找到了: " + data.pageTitle);
        System.out.println("\n耗时：" + duration + " ms");
        System.out.println("注意：未找到即为正常，说明搜索过滤机制正常");
        System.out.println("=====================================\n");
    }

    /**
     * 性能基准测试
     */
    @Test
    public void benchmarkTest() {
        System.out.println("========== 性能基准测试 ==========");
        
        // 预热（避免JIT影响）
        System.out.println("预热中...");
        BangumiCrawler.getTodayAnime();
        
        // 正式测试
        System.out.println("\n开始基准测试（10次请求）...");
        long[] times = new long[10];
        
        for (int i = 0; i < 10; i++) {
            long startTime = System.currentTimeMillis();
            BangumiCrawler.getTodayAnime();
            long endTime = System.currentTimeMillis();
            times[i] = endTime - startTime;
            System.out.println("第 " + (i + 1) + " 次: " + times[i] + " ms");
        }
        
        // 计算统计数据
        long sum = 0;
        long min = times[0];
        long max = times[0];
        
        for (long time : times) {
            sum += time;
            if (time < min) min = time;
            if (time > max) max = time;
        }
        
        double average = sum / 10.0;
        
        System.out.println("\n统计结果：");
        System.out.println("平均耗时：" + average + " ms");
        System.out.println("最快：" + min + " ms");
        System.out.println("最慢：" + max + " ms");
        System.out.println("总耗时：" + sum + " ms");
        System.out.println("=====================================\n");
    }
}

