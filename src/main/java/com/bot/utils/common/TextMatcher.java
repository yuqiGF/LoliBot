package com.bot.utils.common;

/**
 * 文本匹配工具类
 * 提供字符串相似度计算功能
 */
public class TextMatcher {
    
    /**
     * 计算两个字符串的相似度
     * @return 0.0-1.0 的相似度分数
     */
    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
        
        // 完全匹配
        if (s1.equals(s2)) return 1.0;
        
        // 包含关系
        if (s2.contains(s1)) return 0.9 * ((double) s1.length() / s2.length());
        if (s1.contains(s2)) return 0.8 * ((double) s2.length() / s1.length());
        
        // 编辑距离
        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        double editScore = 1.0 - ((double) distance / maxLen);
        
        // 前缀匹配
        int prefixLen = commonPrefixLength(s1, s2);
        double prefixScore = prefixLen > 0 ? 0.3 * ((double) prefixLen / Math.min(s1.length(), s2.length())) : 0.0;
        
        return Math.min(1.0, 0.7 * editScore + 0.3 * prefixScore);
    }
    
    /**
     * Levenshtein 距离（编辑距离）
     */
    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * 公共前缀长度
     */
    private static int commonPrefixLength(String s1, String s2) {
        int minLen = Math.min(s1.length(), s2.length());
        int i = 0;
        while (i < minLen && s1.charAt(i) == s2.charAt(i)) i++;
        return i;
    }
}

