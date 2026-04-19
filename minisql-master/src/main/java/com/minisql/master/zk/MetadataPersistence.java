package com.minisql.master.zk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.minisql.master.metadata.RegionMetadata;
import com.minisql.master.metadata.TableMetadata;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * 元数据持久化管理器
 *
 * 将表和Region元数据持久化到Zookeeper
 */
public class MetadataPersistence {

    private static final Logger logger = LoggerFactory.getLogger(MetadataPersistence.class);

    private static final String METADATA_ROOT = "/minisql/metadata";
    private static final String TABLES_PATH = METADATA_ROOT + "/tables";
    private static final String REGIONS_PATH = METADATA_ROOT + "/regions";

    private final ZookeeperClient zkClient;
    private final Gson gson;

    public MetadataPersistence(ZookeeperClient zkClient) {
        this.zkClient = zkClient;
        this.gson = new Gson();
    }

    /**
     * 初始化元数据路径
     */
    public void initialize() throws KeeperException, InterruptedException {
        zkClient.ensurePath(METADATA_ROOT);
        zkClient.ensurePath(TABLES_PATH);
        zkClient.ensurePath(REGIONS_PATH);
        logger.info("Metadata paths initialized");
    }

    /**
     * 保存表元数据
     */
    public void saveTable(TableMetadata table) throws KeeperException, InterruptedException {
        String path = TABLES_PATH + "/" + table.getTableName();

        // 构建简化的元数据对象（避免序列化protobuf）
        Map<String, Object> tableData = new HashMap<>();
        tableData.put("tableName", table.getTableName());
        tableData.put("partitionKey", table.getPartitionKey());
        tableData.put("regionIds", table.getRegionIds());
        tableData.put("createTime", table.getCreateTime());
        tableData.put("updateTime", table.getUpdateTime());
        tableData.put("version", table.getVersion());

        String json = gson.toJson(tableData);

        if (zkClient.exists(path)) {
            zkClient.setData(path, json);
            logger.debug("Updated table metadata: {}", table.getTableName());
        } else {
            zkClient.createPersistentNode(path, json);
            logger.info("Saved table metadata: {}", table.getTableName());
        }
    }

    /**
     * 加载表元数据
     */
    public Map<String, Object> loadTable(String tableName) throws KeeperException, InterruptedException {
        String path = TABLES_PATH + "/" + tableName;

        if (!zkClient.exists(path)) {
            return null;
        }

        String json = zkClient.getData(path);
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * 删除表元数据
     */
    public void deleteTable(String tableName) throws KeeperException, InterruptedException {
        String path = TABLES_PATH + "/" + tableName;

        if (zkClient.exists(path)) {
            zkClient.deleteNode(path);
            logger.info("Deleted table metadata: {}", tableName);
        }
    }

    /**
     * 保存Region元数据
     */
    public void saveRegion(RegionMetadata region) throws KeeperException, InterruptedException {
        String path = REGIONS_PATH + "/" + region.getRegionId();

        // 构建简化的元数据对象
        Map<String, Object> regionData = new HashMap<>();
        regionData.put("regionId", region.getRegionId());
        regionData.put("tableName", region.getTableName());
        regionData.put("startKey", region.getStartKey());
        regionData.put("endKey", region.getEndKey());
        regionData.put("primaryServer", region.getPrimaryServer());
        regionData.put("replicas", region.getReplicas());
        regionData.put("state", region.getState().name());
        regionData.put("createTime", region.getCreateTime());
        regionData.put("updateTime", region.getUpdateTime());
        regionData.put("version", region.getVersion());

        String json = gson.toJson(regionData);

        if (zkClient.exists(path)) {
            zkClient.setData(path, json);
            logger.debug("Updated region metadata: {}", region.getRegionId());
        } else {
            zkClient.createPersistentNode(path, json);
            logger.info("Saved region metadata: {}", region.getRegionId());
        }
    }

    /**
     * 加载Region元数据
     */
    public Map<String, Object> loadRegion(String regionId) throws KeeperException, InterruptedException {
        String path = REGIONS_PATH + "/" + regionId;

        if (!zkClient.exists(path)) {
            return null;
        }

        String json = zkClient.getData(path);
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * 删除Region元数据
     */
    public void deleteRegion(String regionId) throws KeeperException, InterruptedException {
        String path = REGIONS_PATH + "/" + regionId;

        if (zkClient.exists(path)) {
            zkClient.deleteNode(path);
            logger.info("Deleted region metadata: {}", regionId);
        }
    }

    /**
     * 加载所有表名
     */
    public java.util.List<String> loadAllTableNames() throws KeeperException, InterruptedException {
        return zkClient.getChildren(TABLES_PATH);
    }

    /**
     * 加载所有Region ID
     */
    public java.util.List<String> loadAllRegionIds() throws KeeperException, InterruptedException {
        return zkClient.getChildren(REGIONS_PATH);
    }
}
