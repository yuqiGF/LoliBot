package com.bot.utils.common;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 客户端连接池
 * 提供复用的 HTTP 客户端，支持连接池、重试、超时等功能
 */
public class HttpClientPool {
    
    private static final int TIMEOUT = 15000;
    private static final int MAX_TOTAL = 200;
    private static final int MAX_PER_ROUTE = 20;
    
    private static final List<String> USER_AGENTS = Arrays.asList(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64; rv:144.0) Gecko/20100101 Firefox/144.0"
    );
    
    private static final Random random = new Random();
    private static volatile PoolingHttpClientConnectionManager connectionManager;
    
    /**
     * 获取连接池管理器
     */
    private static PoolingHttpClientConnectionManager getConnectionManager() {
        if (connectionManager == null) {
            synchronized (HttpClientPool.class) {
                if (connectionManager == null) {
                    connectionManager = new PoolingHttpClientConnectionManager();
                    connectionManager.setMaxTotal(MAX_TOTAL);
                    connectionManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);
                    
                    SocketConfig socketConfig = SocketConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .setTcpNoDelay(true)
                            .build();
                    connectionManager.setDefaultSocketConfig(socketConfig);
                }
            }
        }
        return connectionManager;
    }
    
    /**
     * 创建 HTTP 客户端
     */
    public static CloseableHttpClient createClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT)
                .setSocketTimeout(TIMEOUT)
                .setConnectionRequestTimeout(TIMEOUT)
                .setMaxRedirects(5)
                .setCookieSpec(CookieSpecs.STANDARD)
                .setCircularRedirectsAllowed(false)
                .build();
                
        return HttpClients.custom()
                .setConnectionManager(getConnectionManager())
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent(getRandomUserAgent())
                .evictExpiredConnections()
                .evictIdleConnections(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 获取随机 User-Agent
     */
    public static String getRandomUserAgent() {
        return USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
    }
    
    /**
     * 计算重试延迟（指数退避）
     */
    public static long calculateRetryDelay(int retryCount) {
        return 1000L * (long) Math.pow(2, retryCount - 1);
    }
}

