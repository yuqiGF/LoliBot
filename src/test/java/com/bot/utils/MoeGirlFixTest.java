package com.bot.utils;

import com.bot.utils.crawler.MoeGirlCrawler;
import com.bot.utils.crawler.MoeGirlCrawler.InfoboxData;
import org.junit.jupiter.api.Test;

/**
 * 萌娘百科爬虫修复测试
 */
public class MoeGirlFixTest {

    @Test
    public void testWithCorrectDomain() {
        String[] testCases = {
            "宇崎",
            "初音未来",
            "洛天依"
        };

        System.out.println("========== 萌娘百科爬虫测试（修复后）==========\n");
        System.out.println("✅ 已切换域名: moegirl.icu");
        System.out.println("✅ 已添加完整请求头\n");

        int success = 0;
        int fail = 0;

        for (String name : testCases) {
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("测试: " + name);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            long start = System.currentTimeMillis();
            InfoboxData data = MoeGirlCrawler.getInfo(name);
            long duration = System.currentTimeMillis() - start;

            System.out.println("\n耗时: " + duration + "ms");
            if (data != null) {
                System.out.println("标题: " + data.pageTitle);
                System.out.println("图片: " + (data.imageUrl != null ? data.imageUrl : "无"));
                System.out.println("字段数: " + data.fields.size());
                data.fields.forEach((k, v) -> System.out.println("  " + k + ": " + v));
                success++;
                System.out.println("\n✅ 成功");
            } else {
                fail++;
                System.out.println("\n❌ 失败");
            }

            // 避免请求过快
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("\n========================================");
        System.out.println("测试总结: 成功 " + success + "/" + testCases.length +
                         ", 失败 " + fail + "/" + testCases.length);
        System.out.println("========================================");
    }
}

