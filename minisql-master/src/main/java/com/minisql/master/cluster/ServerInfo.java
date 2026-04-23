package com.minisql.master.cluster;

import com.minisql.common.proto.ServerState;
import com.minisql.common.proto.ServerMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * RegionServer信息
 *
 * 封装RegionServer的状态、指标和Region列表
 */
public class ServerInfo {

    private final String serverId;
    private final String host;
    private final int port;
    private volatile ServerState state;
    private volatile long lastHeartbeatTime;
    private volatile ServerMetrics metrics;
    private final Map<String, Long> regions; // regionId -> size
    private final long registrationTime;

    public ServerInfo(String serverId, String host, int port) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.state = ServerState.SERVER_ONLINE;
        this.lastHeartbeatTime = System.currentTimeMillis();
        this.regions = new ConcurrentHashMap<>();
        this.registrationTime = System.currentTimeMillis();
        this.metrics = ServerMetrics.newBuilder().build();
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat() {
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    /**
     * 更新心跳时间和指标
     */
    public void updateHeartbeat(ServerMetrics metrics) {
        this.lastHeartbeatTime = System.currentTimeMillis();
        this.metrics = metrics;
    }

    /**
     * 添加Region
     */
    public void addRegion(String regionId, long size) {
        regions.put(regionId, size);
    }

    /**
     * 移除Region
     */
    public void removeRegion(String regionId) {
        regions.remove(regionId);
    }

    /**
     * 检查是否超时
     */
    public boolean isTimeout(long timeoutMs) {
        return System.currentTimeMillis() - lastHeartbeatTime > timeoutMs;
    }

    /**
     * 获取负载分数（用于负载均衡）
     * 综合考虑Region数量、数据大小、CPU、内存
     */
    public double getLoadScore() {
        double regionScore = regions.size() * 0.5;
        double sizeScore = (metrics.getTotalSizeBytes() / (1024.0 * 1024 * 1024)) * 0.3;
        double cpuScore = metrics.getCpuUsage() * 0.1;
        double memoryScore = metrics.getMemoryUsage() * 0.1;
        return regionScore + sizeScore + cpuScore + memoryScore;
    }

    /**
     * 获取服务器地址
     */
    public String getAddress() {
        return host + ":" + port;
    }

    // Getters

    public String getServerId() {
        return serverId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public ServerState getState() {
        return state;
    }

    public void setState(ServerState state) {
        this.state = state;
    }

    public long getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public ServerMetrics getMetrics() {
        return metrics;
    }

    public Map<String, Long> getRegions() {
        return new ConcurrentHashMap<>(regions);
    }

    /**
     * 获取所有Region的ID列表
     *
     * @return Region ID列表的副本
     */
    public List<String> getRegionIds() {
        return new ArrayList<>(regions.keySet());
    }

    public int getRegionCount() {
        return regions.size();
    }

    public long getRegistrationTime() {
        return registrationTime;
    }

    @Override
    public String toString() {
        return String.format("ServerInfo{id=%s, address=%s, state=%s, regions=%d, load=%.2f}",
                serverId, getAddress(), state, regions.size(), getLoadScore());
    }
}
