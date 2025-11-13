package com.bot.utils;

import com.bot.utils.crawler.MoeGirlCrawler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

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
        System.out.println("✅ 已修复域名: mzh.moegirl.org.cn");
        System.out.println("✅ 已添加完整请求头");
        System.out.println("⚠️ 注意：如遇滑块验证，爬虫可能无法自动绕过\n");
        
        int success = 0;
        int fail = 0;
        
        for (String name : testCases) {
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("测试: " + name);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            long start = System.currentTimeMillis();
            String result = MoeGirlCrawler.getInfo(name);
            long duration = System.currentTimeMillis() - start;
            
            System.out.println("\n结果 (" + duration + "ms):");
            System.out.println(result);
            
            if (result.contains("未找到") || result.contains("失败") || 
                result.contains("验证") || result.contains("未找到详细信息")) {
                fail++;
                System.out.println("\n❌ 失败");
            } else {
                success++;
                System.out.println("\n✅ 成功");
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

