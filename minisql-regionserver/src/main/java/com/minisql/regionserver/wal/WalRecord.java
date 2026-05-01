package com.minisql.regionserver.wal;

import java.util.LinkedHashMap;
import java.util.Map;

public class WalRecord {
    private final long sequenceId;
    private final String regionId;
    private final String tableName;
    private final long timestamp;
    private final String operation;
    private final byte[] key;
    private final Map<String, byte[]> columns;

    public WalRecord(long sequenceId,
                     String regionId,
                     String tableName,
                     long timestamp,
                     String operation,
                     byte[] key,
                     Map<String, byte[]> columns) {
        this.sequenceId = sequenceId;
        this.regionId = regionId;
        this.tableName = tableName;
        this.timestamp = timestamp;
        this.operation = operation;
        this.key = key;
        this.columns = new LinkedHashMap<>(columns);
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getTableName() {
        return tableName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getOperation() {
        return operation;
    }

    public byte[] getKey() {
        return key;
    }

    public Map<String, byte[]> getColumns() {
        return new LinkedHashMap<>(columns);
    }
}
