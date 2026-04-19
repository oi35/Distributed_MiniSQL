package com.minisql.master.balance;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Region迁移任务
 */
public class MigrationTask {

    private final String migrationId;
    private final String regionId;
    private final String sourceServerId;
    private final String targetServerId;
    private volatile MigrationState state;
    private final long createTime;
    private volatile long startTime;
    private volatile long endTime;
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private volatile String errorMessage;
    private final Map<String, Object> metadata;

    public MigrationTask(String migrationId, String regionId,
                        String sourceServerId, String targetServerId) {
        if (migrationId == null || regionId == null ||
            sourceServerId == null || targetServerId == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        if (sourceServerId.equals(targetServerId)) {
            throw new IllegalArgumentException("Source and target server cannot be the same");
        }
        this.migrationId = migrationId;
        this.regionId = regionId;
        this.sourceServerId = sourceServerId;
        this.targetServerId = targetServerId;
        this.state = MigrationState.PENDING;
        this.createTime = System.currentTimeMillis();
        this.startTime = 0;
        this.endTime = 0;
        this.metadata = new ConcurrentHashMap<>();
    }

    public String getMigrationId() {
        return migrationId;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getSourceServerId() {
        return sourceServerId;
    }

    public String getTargetServerId() {
        return targetServerId;
    }

    public MigrationState getState() {
        return state;
    }

    public void setState(MigrationState state) {
        this.state = state;
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public void incrementRetry() {
        retryCount.incrementAndGet();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Map<String, Object> getAllMetadata() {
        return new HashMap<>(metadata);
    }

    public long getDuration() {
        if (startTime == 0) {
            return 0;
        }
        if (endTime == 0) {
            return System.currentTimeMillis() - startTime;
        }
        return endTime - startTime;
    }

    @Override
    public String toString() {
        return String.format("MigrationTask{id=%s, region=%s, %s->%s, state=%s, retry=%d}",
                migrationId, regionId, sourceServerId, targetServerId, state, retryCount.get());
    }
}
