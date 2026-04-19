package com.minisql.master.metadata;

import com.minisql.common.proto.TableSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 表元数据
 *
 * 封装表的元数据信息，包括表名、Schema、分区键、Region列表等
 */
public class TableMetadata {

    private final String tableName;
    private final TableSchema schema;
    private final String partitionKey;
    private final List<String> regionIds;
    private final long createTime;
    private volatile long updateTime;
    private volatile int version;

    public TableMetadata(String tableName, TableSchema schema, String partitionKey) {
        this.tableName = tableName;
        this.schema = schema;
        this.partitionKey = partitionKey;
        this.regionIds = new ArrayList<>();
        this.createTime = System.currentTimeMillis();
        this.updateTime = this.createTime;
        this.version = 1;
    }

    /**
     * 添加Region
     */
    public synchronized void addRegion(String regionId) {
        if (!regionIds.contains(regionId)) {
            regionIds.add(regionId);
            updateVersion();
        }
    }

    /**
     * 移除Region
     */
    public synchronized void removeRegion(String regionId) {
        if (regionIds.remove(regionId)) {
            updateVersion();
        }
    }

    /**
     * 获取所有Region ID
     */
    public synchronized List<String> getRegionIds() {
        return new ArrayList<>(regionIds);
    }

    /**
     * 获取Region数量
     */
    public synchronized int getRegionCount() {
        return regionIds.size();
    }

    /**
     * 更新版本号
     */
    private void updateVersion() {
        this.version++;
        this.updateTime = System.currentTimeMillis();
    }

    // Getters

    public String getTableName() {
        return tableName;
    }

    public TableSchema getSchema() {
        return schema;
    }

    public String getPartitionKey() {
        return partitionKey;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableMetadata that = (TableMetadata) o;
        return Objects.equals(tableName, that.tableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName);
    }

    @Override
    public String toString() {
        return String.format("TableMetadata{name=%s, regions=%d, version=%d}",
                tableName, regionIds.size(), version);
    }
}
