package com.minisql.regionserver.store;

import com.google.protobuf.ByteString;
import com.minisql.regionserver.db.MySQLDatabase;
import com.minisql.regionserver.proto.RowData;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MySQL-backed region store. It uses one SQL table per region.
 */
public class MySqlRegionDataStore implements RegionDataStore {

    private final MySQLDatabase database;
    private final String physicalTableName;

    public MySqlRegionDataStore(MySQLDatabase database, String regionId) {
        this.database = database;
        this.physicalTableName = "region_" + regionId.replaceAll("[^a-zA-Z0-9_]", "_");
        try {
            this.database.createRegionTable(physicalTableName);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create region table " + physicalTableName, e);
        }
    }

    @Override
    public void upsert(byte[] key, Map<String, ByteString> columns, long timestamp) {
        try {
            database.insertRow(physicalTableName, RowData.newBuilder()
                    .setKey(ByteString.copyFrom(key))
                    .putAllColumns(columns)
                    .build());
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to upsert row into " + physicalTableName, e);
        }
    }

    @Override
    public StoredRowRecord get(byte[] key) {
        try {
            RowData row = database.selectRow(physicalTableName, key);
            if (row == null || row.getColumnsCount() == 0) {
                return null;
            }
            return new StoredRowRecord(key.clone(), new LinkedHashMap<>(row.getColumnsMap()), System.currentTimeMillis());
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fetch row from " + physicalTableName, e);
        }
    }

    @Override
    public boolean delete(byte[] key) {
        try {
            return database.deleteRow(physicalTableName, key);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete row from " + physicalTableName, e);
        }
    }

    @Override
    public boolean exists(byte[] key) {
        try {
            return database.existsRow(physicalTableName, key);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check row existence in " + physicalTableName, e);
        }
    }

    @Override
    public List<StoredRowRecord> scan(byte[] startKey, byte[] endKey, boolean reverse) {
        try {
            List<StoredRowRecord> rows = database.scanRows(physicalTableName, startKey, endKey).stream()
                    .map(row -> new StoredRowRecord(
                            row.getKey().toByteArray(),
                            new LinkedHashMap<>(row.getColumnsMap()),
                            System.currentTimeMillis()))
                    .collect(Collectors.toList());
            if (reverse) {
                rows.sort((left, right) -> Arrays.compareUnsigned(right.getKey(), left.getKey()));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to scan rows from " + physicalTableName, e);
        }
    }

    @Override
    public long rowCount() {
        try {
            return database.getTableStats(physicalTableName).rowCount;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count rows for " + physicalTableName, e);
        }
    }

    @Override
    public long sizeBytes() {
        try {
            return database.getTableStats(physicalTableName).sizeBytes;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read size stats for " + physicalTableName, e);
        }
    }

    @Override
    public void close() {
        // shared datasource is closed by the owning service
    }
}
