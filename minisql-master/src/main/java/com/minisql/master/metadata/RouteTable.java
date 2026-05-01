package com.minisql.master.metadata;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 路由表
 *
 * 管理表到Region的路由映射，支持点查询和范围查询
 */
public class RouteTable {

    // 表名 -> Region列表（按startKey排序）
    private final Map<String, List<RegionMetadata>> tableRoutes;

    // Region ID -> RegionMetadata
    private final Map<String, RegionMetadata> regionIndex;

    private final ReadWriteLock lock;

    public RouteTable() {
        this.tableRoutes = new ConcurrentHashMap<>();
        this.regionIndex = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * 添加Region到路由表
     */
    public void addRegion(RegionMetadata region) {
        lock.writeLock().lock();
        try {
            String tableName = region.getTableName();

            // 添加到索引
            regionIndex.put(region.getRegionId(), region);

            // 添加到表路由
            List<RegionMetadata> regions = tableRoutes.computeIfAbsent(
                    tableName,
                    k -> new ArrayList<>()
            );

            regions.add(region);

            // 按startKey排序
            regions.sort(Comparator.comparing(RegionMetadata::getStartKey));

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从路由表移除Region
     */
    public void removeRegion(String regionId) {
        lock.writeLock().lock();
        try {
            RegionMetadata region = regionIndex.remove(regionId);
            if (region != null) {
                String tableName = region.getTableName();
                List<RegionMetadata> regions = tableRoutes.get(tableName);
                if (regions != null) {
                    regions.remove(region);
                    if (regions.isEmpty()) {
                        tableRoutes.remove(tableName);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
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
        lock.readLock().lock();
        try {
            List<RegionMetadata> regions = tableRoutes.get(tableName);
            if (regions == null || regions.isEmpty()) {
                return null;
            }

            // 二分查找
            for (RegionMetadata region : regions) {
                if (region.containsKey(key)) {
                    return region;
                }
            }

            return null;

        } finally {
            lock.readLock().unlock();
        }
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
        lock.readLock().lock();
        try {
            List<RegionMetadata> regions = tableRoutes.get(tableName);
            if (regions == null || regions.isEmpty()) {
                return Collections.emptyList();
            }

            return regions.stream()
                    .filter(region -> region.overlapsRange(startKey, endKey))
                    .collect(Collectors.toList());

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取表的所有Region
     *
     * @param tableName 表名
     * @return Region列表
     */
    public List<RegionMetadata> getTableRegions(String tableName) {
        lock.readLock().lock();
        try {
            List<RegionMetadata> regions = tableRoutes.get(tableName);
            if (regions == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(regions);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 根据Region ID获取Region
     *
     * @param regionId Region ID
     * @return Region元数据，未找到返回null
     */
    public RegionMetadata getRegion(String regionId) {
        return regionIndex.get(regionId);
    }

    /**
     * 获取所有表名
     *
     * @return 表名列表
     */
    public List<String> getAllTableNames() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(tableRoutes.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取所有Region
     *
     * @return Region列表
     */
    public List<RegionMetadata> getAllRegions() {
        return new ArrayList<>(regionIndex.values());
    }

    /**
     * 清空路由表
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            tableRoutes.clear();
            regionIndex.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取Region总数
     */
    public int getRegionCount() {
        return regionIndex.size();
    }

    /**
     * 获取表的Region数量
     */
    public int getTableRegionCount(String tableName) {
        lock.readLock().lock();
        try {
            List<RegionMetadata> regions = tableRoutes.get(tableName);
            return regions == null ? 0 : regions.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
