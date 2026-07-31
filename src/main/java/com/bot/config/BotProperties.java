package com.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 机器人身份及群范围配置。
 *
 * <p>QQ 号与群号属于部署环境信息，只能写入被 Git 忽略的
 * {@code application-local.yml}，不能硬编码进公开仓库。</p>
 */
@Component
@ConfigurationProperties(prefix = "bot")
public class BotProperties {
    private long accountId;
    private long masterQq;
    private long birdQq;
    private long badGayQq;
    private long qiqiQq;
    private long scheduledGroup;
    private Set<Long> advancedGroups = new HashSet<>();

    public boolean isMaster(long userId) {
        return masterQq > 0 && userId == masterQq;
    }

    public boolean isAdvancedGroup(long groupId) {
        return advancedGroups.contains(groupId);
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public long getMasterQq() {
        return masterQq;
    }

    public void setMasterQq(long masterQq) {
        this.masterQq = masterQq;
    }

    public long getBirdQq() {
        return birdQq;
    }

    public void setBirdQq(long birdQq) {
        this.birdQq = birdQq;
    }

    public long getBadGayQq() {
        return badGayQq;
    }

    public void setBadGayQq(long badGayQq) {
        this.badGayQq = badGayQq;
    }

    public long getQiqiQq() {
        return qiqiQq;
    }

    public void setQiqiQq(long qiqiQq) {
        this.qiqiQq = qiqiQq;
    }

    public long getScheduledGroup() {
        return scheduledGroup;
    }

    public void setScheduledGroup(long scheduledGroup) {
        this.scheduledGroup = scheduledGroup;
    }

    public Set<Long> getAdvancedGroups() {
        return advancedGroups;
    }

    public void setAdvancedGroups(Set<Long> advancedGroups) {
        this.advancedGroups = advancedGroups == null ? new HashSet<>() : new HashSet<>(advancedGroups);
    }
}
