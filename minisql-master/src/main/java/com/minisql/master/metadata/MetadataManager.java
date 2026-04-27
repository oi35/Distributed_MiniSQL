package com.minisql.master.metadata;

import com.minisql.common.proto.RegionState;
import com.minisql.common.proto.TableSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 元数据管理器
 *
 * 管理所有表和Region的元数据，提供CRUD操作
 */
public class MetadataManager {

    private static final Logger logger = LoggerFactory.getLogger(MetadataManager.class);

    // 表名 -> TableMetadata
    private final Map<String, TableMetadata> tables;

    // 路由表
    private final RouteTable routeTable;

    // 读写锁
    private final ReadWriteLock lock;

    public MetadataManager() {
        this.tables = new ConcurrentHashMap<>();
        this.routeTable = new RouteTable();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * 创建表
     *
     * @param tableName 表名
     * @param schema 表Schema
     * @param partitionKey 分区键
     * @return 是否成功
     */
    public boolean createTable(String tableName, TableSchema schema, String partitionKey) {
        lock.writeLock().lock();
        try {
            if (tables.containsKey(tableName)) {
                logger.warn("Table already exists: {}", tableName);
                return false;
            }

            TableMetadata tableMetadata = new TableMetadata(tableName, schema, partitionKey);
            tables.put(tableName, tableMetadata);

            logger.info("Table created: {}", tableName);
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 删除表
     *
     * @param tableName 表名
     * @return 是否成功
     */
    public boolean dropTable(String tableName) {
        lock.writeLock().lock();
        try {
            TableMetadata table = tables.remove(tableName);
            if (table == null) {
                logger.warn("Table not found: {}", tableName);
                return false;
            }

            // 删除所有Region
            List<String> regionIds = table.getRegionIds();
            for (String regionId : regionIds) {
                routeTable.removeRegion(regionId);
            }

            logger.info("Table dropped: {}, {} regions removed", tableName, regionIds.size());
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取表元数据
     *
     * @param tableName 表名
     * @return 表元数据，不存在返回null
     */
    public TableMetadata getTable(String tableName) {
        lock.readLock().lock();
        try {
            return tables.get(tableName);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取所有表名
     *
     * @return 表名列表
     */
    public List<String> listTables() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(tables.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 创建Region
     *
     * @param regionId Region ID
     * @param tableName 表名
     * @param startKey 起始键
     * @param endKey 结束键
     * @return 是否成功
     */
    public boolean createRegion(String regionId, String tableName, String startKey, String endKey) {
        lock.writeLock().lock();
        try {
            TableMetadata table = tables.get(tableName);
            if (table == null) {
                logger.warn("Table not found: {}", tableName);
                return false;
            }

            // 检查Region是否已存在
            if (routeTable.getRegion(regionId) != null) {
                logger.warn("Region already exists: {}", regionId);
                return false;
            }

            // 创建Region元数据
            RegionMetadata region = new RegionMetadata(regionId, tableName, startKey, endKey);
            routeTable.addRegion(region);

            // 添加到表的Region列表
            table.addRegion(regionId);

            logger.info("Region created: {} for table: {}, range=[{},{})",
                       regionId, tableName, startKey, endKey);
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 删除Region
     *
     * @param regionId Region ID
     * @return 是否成功
     */
    public boolean deleteRegion(String regionId) {
        lock.writeLock().lock();
        try {
            RegionMetadata region = routeTable.getRegion(regionId);
            if (region == null) {
                logger.warn("Region not found: {}", regionId);
                return false;
            }

            String tableName = region.getTableName();
            TableMetadata table = tables.get(tableName);
            if (table != null) {
                table.removeRegion(regionId);
            }

            routeTable.removeRegion(regionId);

            logger.info("Region deleted: {}", regionId);
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取Region元数据
     *
     * @param regionId Region ID
     * @return Region元数据，不存在返回null
     */
    public RegionMetadata getRegion(String regionId) {
        return routeTable.getRegion(regionId);
    }

    /**
     * 更新Region状态
     *
     * @param regionId Region ID
     * @param state 新状态
     * @return 是否成功
     */
    public boolean updateRegionState(String regionId, RegionState state) {
        RegionMetadata region = routeTable.getRegion(regionId);
        if (region == null) {
            logger.warn("Region not found: {}", regionId);
            return false;
        }

        region.setState(state);
        logger.debug("Region state updated: {} -> {}", regionId, state);
        return true;
    }

    /**
     * 添加Region副本
     *
     * @param regionId Region ID
     * @param serverId 服务器ID
     * @return 是否成功
     */
    public boolean addRegionReplica(String regionId, String serverId) {
        RegionMetadata region = routeTable.getRegion(regionId);
        if (region == null) {
            logger.warn("Region not found: {}", regionId);
            return false;
        }

        region.addReplica(serverId);
        logger.info("Replica added: region={}, server={}", regionId, serverId);
        return true;
    }

    /**
     * 移除Region副本
     *
     * @param regionId Region ID
     * @param serverId 服务器ID
     * @return 是否成功
     */
    public boolean removeRegionReplica(String regionId, String serverId) {
        RegionMetadata region = routeTable.getRegion(regionId);
        if (region == null) {
            logger.warn("Region not found: {}", regionId);
            return false;
        }

        region.removeReplica(serverId);
        logger.info("Replica removed: region={}, server={}", regionId, serverId);
        return true;
    }

    /**
     * 设置Region主副本
     *
     * @param regionId Region ID
     * @param serverId 服务器ID
     * @return 是否成功
     */
    public boolean setRegionPrimary(String regionId, String serverId) {
        RegionMetadata region = routeTable.getRegion(regionId);
        if (region == null) {
            logger.warn("Region not found: {}", regionId);
            return false;
        }

        try {
            region.setPrimaryServer(serverId);
            logger.info("Primary server set: region={}, server={}", regionId, serverId);
            return true;
        } catch (IllegalArgumentException e) {
            logger.error("Failed to set primary server: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 根据键查找Region
     *
     * @param tableName 表名
     * @param key 键
     * @return Region元数据，未找到返回null
     */
    public RegionMetadata findRegionForKey(String tableName, String key) {
        return routeTable.findRegionForKey(tableName, key);
    }

    /**
     * 根据范围查找Region列表
     *
     * @param tableName 表名
     * @param startKey 起始键
     * @param endKey 结束键
     * @return Region列表
     */
    public List<RegionMetadata> findRegionsForRange(String tableName, String startKey, String endKey) {
        return routeTable.findRegionsForRange(tableName, startKey, endKey);
    }

    /**
     * 获取表的所有Region
     *
     * @param tableName 表名
     * @return Region列表
     */
    public List<RegionMetadata> getTableRegions(String tableName) {
        return routeTable.getTableRegions(tableName);
    }

    /**
     * 更新Region位置（主副本）
     *
     * @param regionId Region ID
     * @param serverId 新的主副本服务器ID
     * @return 是否成功
     */
    public boolean updateRegionLocation(String regionId, String serverId) {
        return setRegionPrimary(regionId, serverId);
    }

    /**
     * 获取元数据统计信息
     *
     * @return 统计信息映射
     */
    public Map<String, Object> getStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("tableCount", tables.size());
            stats.put("regionCount", routeTable.getRegionCount());
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
}
