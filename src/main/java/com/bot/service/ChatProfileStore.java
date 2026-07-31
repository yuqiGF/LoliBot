package com.bot.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bot.model.User;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 聊天用户档案仓库。
 *
 * <p>档案键由“会话范围 + QQ 号”组成，因此同一个人在不同群里的昵称、好感度和
 * 自动回复概率互不干扰。数据写入独立 JSON 文件，机器人重启后仍能恢复。</p>
 */
@Component
public class ChatProfileStore {

    private static final Logger logger = LoggerFactory.getLogger(ChatProfileStore.class);

    private final Map<String, User> profiles = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();

    @Value("${chat.state-file:data/chat-profiles.json}")
    private String stateFile;

    @PostConstruct
    public void load() {
        Path path = Path.of(stateFile).toAbsolutePath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JSONObject root = JSON.parseObject(Files.readString(path, StandardCharsets.UTF_8));
            for (String key : root.keySet()) {
                User user = root.getObject(key, User.class);
                if (user != null) {
                    profiles.put(key, user);
                }
            }
            logger.info("已恢复 {} 个聊天用户档案", profiles.size());
        } catch (Exception e) {
            logger.warn("聊天用户档案读取失败，将使用空档案启动: {}", e.getMessage());
        }
    }

    public User getOrCreate(String sessionKey, Supplier<User> creator) {
        User existing = profiles.get(sessionKey);
        if (existing != null) {
            return existing;
        }
        User created = profiles.computeIfAbsent(sessionKey, ignored -> creator.get());
        save();
        return created;
    }

    public void update(String sessionKey, User user) {
        profiles.put(sessionKey, user);
        save();
    }

    /** 使用临时文件替换，防止进程中断留下半份 JSON。 */
    private void save() {
        synchronized (fileLock) {
            Path target = Path.of(stateFile).toAbsolutePath();
            Path parent = target.getParent();
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            try {
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(temp, JSON.toJSONString(profiles), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                logger.warn("聊天用户档案保存失败: {}", e.getMessage());
            }
        }
    }
}