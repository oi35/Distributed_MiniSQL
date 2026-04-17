package com.minisql.master.cluster;

import com.minisql.common.proto.ServerState;
import com.minisql.common.proto.ServerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 集群管理器
 *
 * 负责管理所有RegionServer的注册、心跳、故障检测
 */
public class ClusterManager {

    private static final Logger logger = LoggerFactory.getLogger(ClusterManager.class);

    // RegionServer信息映射: serverId -> ServerInfo
    private final Map<String, ServerInfo> servers;

    // 读写锁保护servers映射
    private final ReadWriteLock lock;

    // 心跳超时时间（毫秒）
    private final long heartbeatTimeoutMs;

    // 心跳间隔（毫秒）
    private final int heartbeatIntervalMs;

    public ClusterManager(long heartbeatTimeoutMs, int heartbeatIntervalMs) {
        this.servers = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        logger.info("ClusterManager initialized: timeout={}ms, interval={}ms",
                heartbeatTimeoutMs, heartbeatIntervalMs);
    }

    /**
     * 注册RegionServer
     *
     * @param serverId 建议的服务器ID
     * @param host 主机地址
     * @param port 端口
     * @return 分配的服务器ID
     */
    public String registerServer(String serverId, String host, int port) {
        lock.writeLock().lock();
        try {
            // 检查是否已存在
            if (servers.containsKey(serverId)) {
                ServerInfo existing = servers.get(serverId);
                if (existing.getState() == ServerState.SERVER_DEAD) {
                    // 死亡的服务器重新注册
                    logger.info("Dead server re-registering: {}", serverId);
                    existing.setState(ServerState.SERVER_ONLINE);
                    existing.updateHeartbeat();
                    return serverId;
                } else {
                    // 已存在且在线，生成新ID
                    serverId = generateUniqueServerId(serverId);
                    logger.warn("Server ID conflict, assigned new ID: {}", serverId);
                }
            }

            // 创建新的ServerInfo
            ServerInfo serverInfo = new ServerInfo(serverId, host, port);
            servers.put(serverId, serverInfo);

            logger.info("RegionServer registered: {} at {}:{}", serverId, host, port);
            return serverId;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 注销RegionServer
     *
     * @param serverId 服务器ID
     * @return 是否成功
     */
    public boolean unregisterServer(String serverId) {
        lock.writeLock().lock();
        try {
            ServerInfo serverInfo = servers.get(serverId);
            if (serverInfo == null) {
                logger.warn("Unregister failed: server not found: {}", serverId);
                return false;
            }

            serverInfo.setState(ServerState.SERVER_OFFLINE);
            logger.info("RegionServer unregistered: {}", serverId);
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 更新心跳
     *
     * @param serverId 服务器ID
     * @param metrics 服务器指标
     * @return 是否成功
     */
    public boolean updateHeartbeat(String serverId, ServerMetrics metrics) {
        lock.readLock().lock();
        try {
            ServerInfo serverInfo = servers.get(serverId);
            if (serverInfo == null) {
                logger.warn("Heartbeat failed: server not found: {}", serverId);
                return false;
            }

            serverInfo.updateHeartbeat(metrics);

            // 如果服务器之前是DEAD状态，恢复为ONLINE
            if (serverInfo.getState() == ServerState.SERVER_DEAD) {
                serverInfo.setState(ServerState.SERVER_ONLINE);
                logger.info("Server recovered from DEAD state: {}", serverId);
            }

            logger.debug("Heartbeat updated: {}", serverId);
            return true;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取服务器信息
     *
     * @param serverId 服务器ID
     * @return 服务器信息，不存在返回null
     */
    public ServerInfo getServerInfo(String serverId) {
        lock.readLock().lock();
        try {
            return servers.get(serverId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取所有在线服务器
     *
     * @return 在线服务器列表
     */
    public List<ServerInfo> getOnlineServers() {
        lock.readLock().lock();
        try {
            return servers.values().stream()
                    .filter(s -> s.getState() == ServerState.SERVER_ONLINE)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取所有服务器
     *
     * @return 所有服务器列表
     */
    public List<ServerInfo> getAllServers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(servers.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 检查超时的服务器
     *
     * @return 超时的服务器ID列表
     */
    public List<String> checkTimeoutServers() {
        lock.writeLock().lock();
        try {
            List<String> timeoutServers = new ArrayList<>();

            for (ServerInfo serverInfo : servers.values()) {
                if (serverInfo.getState() == ServerState.SERVER_ONLINE &&
                    serverInfo.isTimeout(heartbeatTimeoutMs)) {

                    serverInfo.setState(ServerState.SERVER_DEAD);
                    timeoutServers.add(serverInfo.getServerId());

                    logger.error("Server timeout detected: {}, last heartbeat: {}ms ago",
                            serverInfo.getServerId(),
                            System.currentTimeMillis() - serverInfo.getLastHeartbeatTime());
                }
            }

            return timeoutServers;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 选择负载最低的服务器
     *
     * @return 负载最低的服务器，没有在线服务器返回null
     */
    public ServerInfo selectLeastLoadedServer() {
        lock.readLock().lock();
        try {
            return servers.values().stream()
                    .filter(s -> s.getState() == ServerState.SERVER_ONLINE)
                    .min(Comparator.comparingDouble(ServerInfo::getLoadScore))
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 添加Region到服务器
     *
     * @param serverId 服务器ID
     * @param regionId Region ID
     * @param size Region大小
     */
    public void addRegionToServer(String serverId, String regionId, long size) {
        lock.readLock().lock();
        try {
            ServerInfo serverInfo = servers.get(serverId);
            if (serverInfo != null) {
                serverInfo.addRegion(regionId, size);
                logger.debug("Region {} added to server {}", regionId, serverId);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 从服务器移除Region
     *
     * @param serverId 服务器ID
     * @param regionId Region ID
     */
    public void removeRegionFromServer(String serverId, String regionId) {
        lock.readLock().lock();
        try {
            ServerInfo serverInfo = servers.get(serverId);
            if (serverInfo != null) {
                serverInfo.removeRegion(regionId);
                logger.debug("Region {} removed from server {}", regionId, serverId);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取集群统计信息
     *
     * @return 统计信息映射
     */
    public Map<String, Object> getClusterStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();

            long totalServers = servers.size();
            long onlineServers = servers.values().stream()
                    .filter(s -> s.getState() == ServerState.SERVER_ONLINE)
                    .count();
            long deadServers = servers.values().stream()
                    .filter(s -> s.getState() == ServerState.SERVER_DEAD)
                    .count();

            int totalRegions = servers.values().stream()
                    .mapToInt(ServerInfo::getRegionCount)
                    .sum();

            stats.put("totalServers", totalServers);
            stats.put("onlineServers", onlineServers);
            stats.put("deadServers", deadServers);
            stats.put("totalRegions", totalRegions);

            return stats;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取心跳间隔
     */
    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /**
     * 生成唯一的服务器ID
     */
    private String generateUniqueServerId(String baseId) {
        int suffix = 1;
        String newId = baseId + "-" + suffix;
        while (servers.containsKey(newId)) {
            suffix++;
            newId = baseId + "-" + suffix;
        }
        return newId;
    }
}
