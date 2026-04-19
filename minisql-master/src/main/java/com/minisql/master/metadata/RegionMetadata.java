package com.minisql.master.metadata;

import com.minisql.common.proto.RegionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Region元数据
 *
 * 封装Region的元数据信息，包括Region ID、所属表、范围、副本等
 */
public class RegionMetadata {

    private final String regionId;
    private final String tableName;
    private final String startKey;
    private final String endKey;
    private final List<String> replicas; // 副本所在的服务器ID列表
    private volatile String primaryServer; // 主副本所在的服务器ID
    private volatile RegionState state;
    private final long createTime;
    private volatile long updateTime;
    private volatile int version;

    public RegionMetadata(String regionId, String tableName, String startKey, String endKey) {
        this.regionId = regionId;
        this.tableName = tableName;
        this.startKey = startKey;
        this.endKey = endKey;
        this.replicas = new ArrayList<>();
        this.primaryServer = null;
        this.state = RegionState.REGION_OFFLINE;
        this.createTime = System.currentTimeMillis();
        this.updateTime = this.createTime;
        this.version = 1;
    }

    /**
     * 添加副本
     */
    public synchronized void addReplica(String serverId) {
        if (!replicas.contains(serverId)) {
            replicas.add(serverId);
            updateVersion();
        }
    }

    /**
     * 移除副本
     */
    public synchronized void removeReplica(String serverId) {
        if (replicas.remove(serverId)) {
            // 如果移除的是主副本，清空主副本
            if (serverId.equals(primaryServer)) {
                primaryServer = null;
            }
            updateVersion();
        }
    }

    /**
     * 设置主副本
     */
    public synchronized void setPrimaryServer(String serverId) {
        if (!replicas.contains(serverId)) {
            throw new IllegalArgumentException("Server " + serverId + " is not a replica");
        }
        this.primaryServer = serverId;
        updateVersion();
    }

    /**
     * 获取所有副本
     */
    public synchronized List<String> getReplicas() {
        return new ArrayList<>(replicas);
    }

    /**
     * 检查键是否在Region范围内
     *
     * @param key 键
     * @return 是否在范围内
     */
    public boolean containsKey(String key) {
        // startKey <= key < endKey
        boolean afterStart = startKey.isEmpty() || key.compareTo(startKey) >= 0;
        boolean beforeEnd = endKey.isEmpty() || key.compareTo(endKey) < 0;
        return afterStart && beforeEnd;
    }

    /**
     * 检查范围是否与Region有交集
     *
     * @param rangeStart 范围起始键
     * @param rangeEnd 范围结束键
     * @return 是否有交集
     */
    public boolean overlapsRange(String rangeStart, String rangeEnd) {
        // 检查两个范围是否有交集
        // [startKey, endKey) 与 [rangeStart, rangeEnd) 是否有交集

        // 如果rangeEnd <= startKey，没有交集
        if (!rangeEnd.isEmpty() && !startKey.isEmpty() && rangeEnd.compareTo(startKey) <= 0) {
            return false;
        }

        // 如果rangeStart >= endKey，没有交集
        if (!rangeStart.isEmpty() && !endKey.isEmpty() && rangeStart.compareTo(endKey) >= 0) {
            return false;
        }

        return true;
    }

    /**
     * 更新版本号
     */
    private void updateVersion() {
        this.version++;
        this.updateTime = System.currentTimeMillis();
    }

    // Getters and Setters

    public String getRegionId() {
        return regionId;
    }

    public String getTableName() {
        return tableName;
    }

    public String getStartKey() {
        return startKey;
    }

    public String getEndKey() {
        return endKey;
    }

    public String getPrimaryServer() {
        return primaryServer;
    }

    public RegionState getState() {
        return state;
    }

    public synchronized void setState(RegionState state) {
        this.state = state;
        updateVersion();
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public int getVersion() {
        return version;
    }

    public int getReplicaCount() {
        return replicas.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegionMetadata that = (RegionMetadata) o;
        return Objects.equals(regionId, that.regionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regionId);
    }

    @Override
    public String toString() {
        return String.format("RegionMetadata{id=%s, table=%s, range=[%s,%s), replicas=%d, primary=%s, state=%s}",
                regionId, tableName, startKey, endKey, replicas.size(), primaryServer, state);
    }
}
