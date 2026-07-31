package com.bot.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语录添加的两步会话状态。
 *
 * <p>状态键包含群号和发起人 QQ，群内多人同时添加时互不干扰。完成后短暂保留
 * 消息指纹，让其他群消息处理器无论执行先后都不会对入库内容再次聊天回复。</p>
 */
@Component
public class QuoteCaptureState {
    private static final long PENDING_MILLIS = Duration.ofMinutes(2).toMillis();
    private static final long CAPTURED_MILLIS = Duration.ofSeconds(10).toMillis();

    private final Map<SessionKey, Pending> pending = new ConcurrentHashMap<>();
    private final Map<SessionKey, Captured> captured = new ConcurrentHashMap<>();

    public void begin(long groupId, long userId, String keyword) {
        pending.put(new SessionKey(groupId, userId),
                new Pending(keyword, System.currentTimeMillis() + PENDING_MILLIS));
    }

    public Pending current(long groupId, long userId) {
        SessionKey key = new SessionKey(groupId, userId);
        Pending value = pending.get(key);
        if (value != null && value.expiresAt() < System.currentTimeMillis()) {
            pending.remove(key, value);
            return null;
        }
        return value;
    }

    public void complete(long groupId, long userId, String rawMessage) {
        SessionKey key = new SessionKey(groupId, userId);
        pending.remove(key);
        captured.put(key, new Captured(fingerprint(rawMessage),
                System.currentTimeMillis() + CAPTURED_MILLIS));
    }

    public boolean cancel(long groupId, long userId) {
        return pending.remove(new SessionKey(groupId, userId)) != null;
    }

    public boolean shouldSkipOtherHandlers(long groupId, long userId, String rawMessage) {
        SessionKey key = new SessionKey(groupId, userId);
        if (current(groupId, userId) != null) return true;
        Captured value = captured.get(key);
        if (value == null) return false;
        if (value.expiresAt() < System.currentTimeMillis()) {
            captured.remove(key, value);
            return false;
        }
        return value.fingerprint() == fingerprint(rawMessage);
    }

    private static int fingerprint(String value) {
        return value == null ? 0 : value.hashCode();
    }

    private record SessionKey(long groupId, long userId) {
    }

    public record Pending(String keyword, long expiresAt) {
    }

    private record Captured(int fingerprint, long expiresAt) {
    }
}