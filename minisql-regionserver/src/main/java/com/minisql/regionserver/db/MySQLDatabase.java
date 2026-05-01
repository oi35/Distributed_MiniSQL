package com.minisql.regionserver.db;

import com.google.protobuf.ByteString;
import com.minisql.regionserver.proto.RowData;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL数据库操作类
 * 负责Region数据的存储和查询
 */
public class MySQLDatabase {
    private static final Logger logger = LoggerFactory.getLogger(MySQLDatabase.class);

    private final HikariDataSource dataSource;

    public MySQLDatabase(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(config);
        logger.info("MySQL database connection pool initialized");
    }

    /**
     * 创建Region表
     */
    public void createRegionTable(String tableName) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS `" + tableName + "` (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "row_key VARBINARY(1024) NOT NULL," +
                "family VARCHAR(255) NOT NULL," +
                "qualifier VARCHAR(255) NOT NULL," +
                "value LONGBLOB," +
                "timestamp BIGINT NOT NULL," +
                "UNIQUE KEY uk_row_family_qualifier (row_key(255), family(50), qualifier(50))" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            logger.info("Created table: {}", tableName);
        }
    }

    /**
     * 插入单行数据
     */
    public void insertRow(String tableName, RowData row) throws SQLException {
        if (row == null || row.getKey() == null || row.getColumnsCount() == 0) {
            return;
        }

        String sql = "INSERT INTO `" + tableName + "` (row_key, family, qualifier, value, timestamp) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), timestamp = VALUES(timestamp)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, ByteString> entry : row.getColumnsMap().entrySet()) {
                stmt.setBytes(1, row.getKey().toByteArray());
                stmt.setString(2, "");
                stmt.setString(3, entry.getKey());
                stmt.setBytes(4, entry.getValue().toByteArray());
                stmt.setLong(5, System.currentTimeMillis());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * 查询单行数据
     */
    public RowData selectRow(String tableName, byte[] rowKey) throws SQLException {
        String sql = "SELECT qualifier, value FROM `" + tableName + "` " +
                "WHERE row_key = ? ORDER BY qualifier";

        Map<String, ByteString> columns = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBytes(1, rowKey);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String qualifier = rs.getString("qualifier");
                    byte[] value = rs.getBytes("value");
                    columns.put(qualifier, ByteString.copyFrom(value));
                }
            }
        }

        return RowData.newBuilder()
                .setKey(ByteString.copyFrom(rowKey))
                .putAllColumns(columns)
                .build();
    }

    /**
     * 删除单行数据
     */
    public boolean deleteRow(String tableName, byte[] rowKey) throws SQLException {
        String sql = "DELETE FROM `" + tableName + "` WHERE row_key = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBytes(1, rowKey);
            int deleted = stmt.executeUpdate();
            return deleted > 0;
        }
    }

    /**
     * 检查行是否存在
     */
    public boolean existsRow(String tableName, byte[] rowKey) throws SQLException {
        String sql = "SELECT 1 FROM `" + tableName + "` WHERE row_key = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBytes(1, rowKey);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 批量插入
     */
    public void batchInsert(String tableName, List<RowData> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO `" + tableName + "` (row_key, family, qualifier, value, timestamp) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), timestamp = VALUES(timestamp)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (RowData row : rows) {
                if (row == null || row.getKey() == null || row.getColumnsCount() == 0) {
                    continue;
                }
                for (Map.Entry<String, ByteString> entry : row.getColumnsMap().entrySet()) {
                    stmt.setBytes(1, row.getKey().toByteArray());
                    stmt.setString(2, "");
                    stmt.setString(3, entry.getKey());
                    stmt.setBytes(4, entry.getValue().toByteArray());
                    stmt.setLong(5, System.currentTimeMillis());
                    stmt.addBatch();
                }
            }

            stmt.executeBatch();
        }
    }

    /**
     * 批量查询
     */
    public List<RowData> batchSelect(String tableName, List<byte[]> rowKeys) throws SQLException {
        List<RowData> results = new ArrayList<>();
        if (rowKeys == null || rowKeys.isEmpty()) {
            return results;
        }

        for (byte[] rowKey : rowKeys) {
            RowData row = selectRow(tableName, rowKey);
            if (row != null && row.getColumnsCount() > 0) {
                results.add(row);
            }
        }

        return results;
    }

    /**
     * 批量删除
     */
    public int batchDelete(String tableName, List<byte[]> rowKeys) throws SQLException {
        String sql = "DELETE FROM `" + tableName + "` WHERE row_key = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (byte[] rowKey : rowKeys) {
                stmt.setBytes(1, rowKey);
                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            int totalDeleted = 0;
            for (int count : results) {
                if (count > 0) totalDeleted += count;
            }
            return totalDeleted;
        }
    }

    /**
     * 范围扫描
     */
    public void scanRows(String tableName, byte[] startKey, byte[] endKey, ScanCallback callback) throws SQLException {
        String sql = "SELECT row_key, qualifier, value FROM `" + tableName + "` " +
                "WHERE row_key >= ? AND row_key < ? ORDER BY row_key, qualifier";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBytes(1, startKey);
            stmt.setBytes(2, endKey);

            try (ResultSet rs = stmt.executeQuery()) {
                byte[] currentRowKey = null;
                RowData.Builder currentRow = null;
                Map<String, ByteString> currentColumns = null;

                while (rs.next()) {
                    byte[] rowKey = rs.getBytes("row_key");
                    String qualifier = rs.getString("qualifier");
                    byte[] value = rs.getBytes("value");

                    if (currentRowKey == null || !java.util.Arrays.equals(currentRowKey, rowKey)) {
                        if (currentRow != null && currentColumns != null && !currentColumns.isEmpty()) {
                            callback.onRow(currentRow.putAllColumns(currentColumns).build());
                        }
                        currentRowKey = rowKey.clone();
                        currentColumns = new HashMap<>();
                        currentRow = RowData.newBuilder().setKey(ByteString.copyFrom(rowKey));
                    }
                    currentColumns.put(qualifier, ByteString.copyFrom(value));
                }

                if (currentRow != null && currentColumns != null && !currentColumns.isEmpty()) {
                    callback.onRow(currentRow.putAllColumns(currentColumns).build());
                }
            }
        }
    }

    public List<RowData> scanRows(String tableName, byte[] startKey, byte[] endKey) throws SQLException {
        List<RowData> rows = new ArrayList<>();
        scanRows(tableName, startKey, endKey, rows::add);
        return rows;
    }

    /**
     * 获取表统计信息
     */
    public TableStats getTableStats(String tableName) throws SQLException {
        String countSql = "SELECT COUNT(*) as row_count, SUM(LENGTH(value)) as size_bytes FROM `" + tableName + "`";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(countSql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                long rowCount = rs.getLong("row_count");
                long sizeBytes = rs.getLong("size_bytes");
                return new TableStats(rowCount, sizeBytes);
            }
        }

        return new TableStats(0, 0);
    }

    /**
     * 关闭数据库连接池
     */
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            logger.info("MySQL database connection pool closed");
        }
    }

    /**
     * 扫描回调接口
     */
    public interface ScanCallback {
        void onRow(RowData row);
    }

    /**
     * 表统计信息
     */
    public static class TableStats {
        public final long rowCount;
        public final long sizeBytes;

        public TableStats(long rowCount, long sizeBytes) {
            this.rowCount = rowCount;
            this.sizeBytes = sizeBytes;
        }
    }
}
