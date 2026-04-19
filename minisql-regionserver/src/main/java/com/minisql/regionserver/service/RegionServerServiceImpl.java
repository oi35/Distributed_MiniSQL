package com.minisql.regionserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RegionServer服务实现
 *
 * 实现RegionServer的gRPC服务接口，提供数据存储和查询功能
 * 注意：当前为简化版本，不依赖 protobuf 生成的类
 */
public class RegionServerServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(RegionServerServiceImpl.class);

    private final String regionServerId;

    // 简单的内存存储，用于原型阶段
    // TODO: 替换为MySQL存储
    private final Map<String, Map<String, Map<String, byte[]>>> tableData = new ConcurrentHashMap<>();

    public RegionServerServiceImpl(String regionServerId) {
        this.regionServerId = regionServerId;
        logger.info("RegionServerServiceImpl initialized for {}", regionServerId);
    }

    // ========== 基础 CRUD 操作（简化版本）=========

    /**
     * 插入数据
     */
    public boolean put(String tableName, String regionId, String key, Map<String, byte[]> columns) {
        try {
            logger.info("PUT operation: table={}, region={}, key={}", tableName, regionId, key);

            // 获取或创建表数据
            Map<String, Map<String, byte[]>> table = tableData.computeIfAbsent(tableName, k -> new ConcurrentHashMap<>());
            Map<String, byte[]> row = table.computeIfAbsent(key, k -> new ConcurrentHashMap<>());

            // 存储列数据
            row.putAll(columns);

            logger.info("PUT operation completed successfully");
            return true;

        } catch (Exception e) {
            logger.error("PUT operation failed", e);
            return false;
        }
    }

    /**
     * 查询数据
     */
    public Map<String, byte[]> get(String tableName, String regionId, String key) {
        try {
            logger.info("GET operation: table={}, region={}, key={}", tableName, regionId, key);

            // 获取表数据
            Map<String, Map<String, byte[]>> table = tableData.get(tableName);
            if (table == null) {
                logger.info("GET operation completed: table not found");
                return null;
            }

            // 获取行数据
            Map<String, byte[]> row = table.get(key);
            if (row == null) {
                logger.info("GET operation completed: key not found");
                return null;
            }

            logger.info("GET operation completed successfully");
            return new HashMap<>(row); // 返回副本

        } catch (Exception e) {
            logger.error("GET operation failed", e);
            return null;
        }
    }

    /**
     * 删除数据
     */
    public boolean delete(String tableName, String regionId, String key) {
        try {
            logger.info("DELETE operation: table={}, region={}, key={}", tableName, regionId, key);

            // 获取表数据
            Map<String, Map<String, byte[]>> table = tableData.get(tableName);
            boolean existed = false;
            if (table != null) {
                existed = table.remove(key) != null;
            }

            logger.info("DELETE operation completed successfully, existed={}", existed);
            return existed;

        } catch (Exception e) {
            logger.error("DELETE operation failed", e);
            return false;
        }
    }

    /**
     * 检查数据是否存在
     */
    public boolean exists(String tableName, String regionId, String key) {
        try {
            logger.info("EXISTS operation: table={}, region={}, key={}", tableName, regionId, key);

            boolean exists = false;
            Map<String, Map<String, byte[]>> table = tableData.get(tableName);
            if (table != null) {
                exists = table.containsKey(key);
            }

            logger.info("EXISTS operation completed, exists={}", exists);
            return exists;

        } catch (Exception e) {
            logger.error("EXISTS operation failed", e);
            return false;
        }
    }
}