package com.minisql.regionserver.service;

import com.google.protobuf.ByteString;
import com.minisql.regionserver.db.MySQLDatabase;
import com.minisql.regionserver.proto.*;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RegionInfo;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RegionServer service implementation
 */
public class RegionServerServiceImpl extends RegionServerServiceGrpc.RegionServerServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(RegionServerServiceImpl.class);

    private final String regionServerId;
    private final MySQLDatabase database;
    private final Map<String, RegionInfo> activeRegions = new ConcurrentHashMap<>();
    private volatile long sequenceId = 0;

    public RegionServerServiceImpl(String regionServerId) {
        this.regionServerId = regionServerId;
        this.database = new MySQLDatabase("jdbc:mysql://localhost:3306/minisql", "root", "password");
        logger.info("RegionServerServiceImpl initialized for {}", regionServerId);
    }

    public RegionServerServiceImpl(String regionServerId, java.util.Properties properties) {
        this.regionServerId = regionServerId;
        String jdbcUrl = properties.getProperty("mysql.url", "jdbc:mysql://localhost:3306/minisql");
        String username = properties.getProperty("mysql.username", "root");
        String password = properties.getProperty("mysql.password", "password");
        this.database = new MySQLDatabase(jdbcUrl, username, password);
        logger.info("RegionServerServiceImpl initialized for {} with custom properties", regionServerId);
    }

    public boolean put(String tableName, String regionId, String key, Map<String, byte[]> columns) {
        if (!isRegionOnline(regionId)) {
            return false;
        }
        try {
            insertRow(tableName, key.getBytes(StandardCharsets.UTF_8), columns);
            return true;
        } catch (Exception e) {
            logger.error("Local PUT failed", e);
            return false;
        }
    }

    public Map<String, byte[]> get(String tableName, String regionId, String key) {
        if (!isRegionOnline(regionId)) {
            return null;
        }
        try {
            Map<String, byte[]> columns = selectRow(tableName, key.getBytes(StandardCharsets.UTF_8));
            if (columns == null || columns.isEmpty()) {
                return null;
            }
            return columns;
        } catch (Exception e) {
            logger.error("Local GET failed", e);
            return null;
        }
    }

    public boolean delete(String tableName, String regionId, String key) {
        if (!isRegionOnline(regionId)) {
            return false;
        }
        try {
            return deleteRow(tableName, key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Local DELETE failed", e);
            return false;
        }
    }

    public boolean exists(String tableName, String regionId, String key) {
        if (!isRegionOnline(regionId)) {
            return false;
        }
        try {
            return existsRow(tableName, key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Local EXISTS failed", e);
            return false;
        }
    }

    private Map<String, byte[]> toByteArrayMap(Map<String, ByteString> columns) {
        Map<String, byte[]> result = new HashMap<>();
        if (columns == null) {
            return result;
        }
        for (Map.Entry<String, ByteString> entry : columns.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toByteArray());
        }
        return result;
    }

    private List<Integer> getAllIndices(int count) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            indices.add(i);
        }
        return indices;
    }

    private List<Long> generateSequenceIds(int count) {
        List<Long> sequenceIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sequenceIds.add(++sequenceId);
        }
        return sequenceIds;
    }

    // Single row operations
    @Override
    public void put(PutRequest request, StreamObserver<PutResponse> responseObserver) {
        try {
            logger.info("PUT operation: table={}, region={}, row={}",
                request.getTableName(), request.getRegionId(),
                bytesToHex(request.getKey().toByteArray()));

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onNext(PutResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
                responseObserver.onCompleted();
                return;
            }

            insertRow(request.getTableName(), request.getKey().toByteArray(), toByteArrayMap(request.getColumnsMap()));

            responseObserver.onNext(PutResponse.newBuilder()
                .setSuccess(true)
                .setErrorCode(ErrorCode.ERROR_OK)
                .build());

            logger.info("PUT operation completed");

        } catch (Exception e) {
            logger.error("PUT operation failed", e);
            responseObserver.onNext(PutResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(ErrorCode.ERROR_INTERNAL)
                .setErrorMessage(e.getMessage())
                .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        try {
            logger.info("GET operation: table={}, region={}, row={}",
                request.getTableName(), request.getRegionId(),
                bytesToHex(request.getKey().toByteArray()));

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onNext(GetResponse.newBuilder()
                    .setFound(false)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
                responseObserver.onCompleted();
                return;
            }

            Map<String, byte[]> columns = selectRow(request.getTableName(), request.getKey().toByteArray());

            GetResponse.Builder getBuilder = GetResponse.newBuilder()
                .setFound(columns != null && !columns.isEmpty())
                .setTimestamp(System.currentTimeMillis())
                .setErrorCode(ErrorCode.ERROR_OK);

            if (columns != null) {
                getBuilder.putAllColumns(toByteStringMap(columns));
            }

            responseObserver.onNext(getBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("GET operation failed", e);
            responseObserver.onNext(GetResponse.newBuilder()
                .setFound(false)
                .setErrorCode(ErrorCode.ERROR_INTERNAL)
                .setErrorMessage(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<DeleteResponse> responseObserver) {
        try {
            logger.info("DELETE operation: table={}, region={}, row={}",
                request.getTableName(), request.getRegionId(),
                bytesToHex(request.getKey().toByteArray()));

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onNext(DeleteResponse.newBuilder()
                    .setSuccess(false)
                    .setExisted(false)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
                responseObserver.onCompleted();
                return;
            }

            boolean deleted = deleteRow(request.getTableName(), request.getKey().toByteArray());

            responseObserver.onNext(DeleteResponse.newBuilder()
                .setSuccess(deleted)
                .setExisted(deleted)
                .setErrorCode(deleted ? ErrorCode.ERROR_OK : ErrorCode.ERROR_NOT_FOUND)
                .setErrorMessage(deleted ? "" : "Row not found")
                .build());

            logger.info("DELETE operation completed: deleted={}", deleted);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("DELETE operation failed", e);
            responseObserver.onNext(DeleteResponse.newBuilder()
                .setSuccess(false)
                .setExisted(false)
                .setErrorCode(ErrorCode.ERROR_INTERNAL)
                .setErrorMessage(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void exists(ExistsRequest request, StreamObserver<ExistsResponse> responseObserver) {
        try {
            logger.info("EXISTS operation: table={}, region={}, row={}",
                request.getTableName(), request.getRegionId(),
                bytesToHex(request.getKey().toByteArray()));

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onNext(ExistsResponse.newBuilder()
                    .setExists(false)
                    .build());
                responseObserver.onCompleted();
                return;
            }

            boolean exists = existsRow(request.getTableName(), request.getKey().toByteArray());

            responseObserver.onNext(ExistsResponse.newBuilder()
                .setExists(exists)
                .build());

            logger.info("EXISTS operation completed: exists={}", exists);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("EXISTS operation failed", e);
            responseObserver.onError(e);
        }
    }

    // Batch operations
    @Override
    public void batchPut(BatchPutRequest request, StreamObserver<BatchPutResponse> responseObserver) {
        try {
            logger.info("BATCH_PUT operation: table={}, region={}, rows={}",
                request.getTableName(), request.getRegionId(), request.getRowsCount());

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onNext(BatchPutResponse.newBuilder()
                    .setSuccessCount(0)
                    .setFailedCount(request.getRowsCount())
                    .addAllFailedIndices(getAllIndices(request.getRowsCount()))
                    .build());
                responseObserver.onCompleted();
                return;
            }

            batchInsert(request.getTableName(), request.getRowsList());

            responseObserver.onNext(BatchPutResponse.newBuilder()
                .setSuccessCount(request.getRowsCount())
                .setFailedCount(0)
                .addAllSequenceIds(generateSequenceIds(request.getRowsCount()))
                .build());

            logger.info("BATCH_PUT operation completed: processed={}", request.getRowsCount());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("BATCH_PUT operation failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void batchGet(BatchGetRequest request, StreamObserver<BatchGetResponse> responseObserver) {
        try {
            logger.info("BATCH_GET operation: table={}, region={}, keys={}",
                request.getTableName(), request.getRegionId(), request.getKeysCount());

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onNext(BatchGetResponse.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }

            List<GetResult> results = new ArrayList<>();
            for (ByteString key : request.getKeysList()) {
                Map<String, byte[]> columns = selectRow(request.getTableName(), key.toByteArray());
                GetResult.Builder resultBuilder = GetResult.newBuilder()
                    .setFound(columns != null && !columns.isEmpty())
                    .setTimestamp(System.currentTimeMillis());
                if (columns != null) {
                    resultBuilder.putAllColumns(toByteStringMap(columns));
                }
                results.add(resultBuilder.build());
            }

            responseObserver.onNext(BatchGetResponse.newBuilder()
                .addAllResults(results)
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("BATCH_GET operation failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void batchDelete(BatchDeleteRequest request, StreamObserver<BatchDeleteResponse> responseObserver) {
        try {
            logger.info("BATCH_DELETE operation: table={}, region={}, keys={}",
                request.getTableName(), request.getRegionId(), request.getKeysCount());

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onNext(BatchDeleteResponse.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }

            int deletedCount = 0;
            List<Boolean> results = new ArrayList<>();
            for (ByteString key : request.getKeysList()) {
                boolean deleted = deleteRow(request.getTableName(), key.toByteArray());
                results.add(deleted);
                if (deleted) {
                    deletedCount++;
                }
            }

            responseObserver.onNext(BatchDeleteResponse.newBuilder()
                .setDeletedCount(deletedCount)
                .addAllResults(results)
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("BATCH_DELETE operation failed", e);
            responseObserver.onError(e);
        }
    }

    // Range scan
    @Override
    public void scan(ScanRequest request, StreamObserver<ScanResponse> responseObserver) {
        try {
            logger.info("SCAN operation: table={}, region={}, start={}, end={}",
                request.getTableName(), request.getRegionId(),
                bytesToHex(request.getStartKey().toByteArray()),
                bytesToHex(request.getEndKey().toByteArray()));

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onError(new RuntimeException("Region not online: " + request.getRegionId()));
                return;
            }

            scanRows(request, responseObserver);
            logger.info("SCAN operation completed");

        } catch (Exception e) {
            logger.error("SCAN operation failed", e);
            responseObserver.onError(e);
        }
    }

    // Advanced query
    @Override
    public void query(QueryRequest request, StreamObserver<QueryResponse> responseObserver) {
        try {
            logger.info("QUERY operation: table={}, region={}",
                request.getTableName(), request.getRegionId());

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onNext(QueryResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
                responseObserver.onCompleted();
                return;
            }

            QueryResponse.Builder responseBuilder = QueryResponse.newBuilder()
                .setSuccess(true)
                .setErrorCode(ErrorCode.ERROR_OK);

            if (request.hasCount()) {
                long count = executeCountQuery(request.getTableName(), request.getRegionId(), request.getCount());
                responseBuilder.setCountResult(CountResult.newBuilder().setCount(count));
            } else if (request.hasAggregate()) {
                AggregateResult aggResult = executeAggregateQuery(request.getTableName(), request.getRegionId(), request.getAggregate());
                responseBuilder.setAggregateResult(aggResult);
            } else if (request.hasFilter()) {
                FilterResult filterResult = executeFilterQuery(request.getTableName(), request.getRegionId(), request.getFilter());
                responseBuilder.setFilterResult(filterResult);
            }

            responseObserver.onNext(responseBuilder.build());
            logger.info("QUERY operation completed");

        } catch (Exception e) {
            logger.error("QUERY operation failed", e);
            responseObserver.onNext(QueryResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(ErrorCode.ERROR_INTERNAL)
                .setErrorMessage(e.getMessage())
                .build());
        }
        responseObserver.onCompleted();
    }

    // Region management
    @Override
    public void openRegion(OpenRegionRequest request, StreamObserver<OpenRegionResponse> responseObserver) {
        try {
            String regionId = request.getRegion().getRegionId();
            logger.info("OPEN_REGION operation: region={}", regionId);

            createRegionTable(request.getRegion());
            activeRegions.put(regionId, request.getRegion());
            RegionStats stats = getRegionStats(request.getRegion());

            responseObserver.onNext(OpenRegionResponse.newBuilder()
                .setSuccess(true)
                .setErrorCode(ErrorCode.ERROR_OK)
                .setSizeBytes(stats.sizeBytes)
                .setRowCount(stats.rowCount)
                .build());

            logger.info("OPEN_REGION operation completed: region={}, size={}, rows={}",
                regionId, stats.sizeBytes, stats.rowCount);

        } catch (Exception e) {
            logger.error("OPEN_REGION operation failed", e);
            responseObserver.onNext(OpenRegionResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(ErrorCode.ERROR_INTERNAL)
                .setErrorMessage(e.getMessage())
                .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void closeRegion(CloseRegionRequest request, StreamObserver<CloseRegionResponse> responseObserver) {
        try {
            logger.info("CLOSE_REGION operation: region={}, force={}",
                request.getRegionId(), request.getForce());

            RegionInfo removed = activeRegions.remove(request.getRegionId());

            responseObserver.onNext(CloseRegionResponse.newBuilder()
                .setSuccess(removed != null)
                .setErrorCode(removed != null ? ErrorCode.ERROR_OK : ErrorCode.ERROR_REGION_NOT_FOUND)
                .setErrorMessage(removed == null ? "Region not found" : "")
                .build());

            logger.info("CLOSE_REGION operation completed: region={}, existed={}",
                request.getRegionId(), removed != null);

        } catch (Exception e) {
            logger.error("CLOSE_REGION operation failed", e);
            responseObserver.onNext(CloseRegionResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(ErrorCode.ERROR_INTERNAL)
                .setErrorMessage(e.getMessage())
                .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void migrateRegion(MigrateRegionRequest request, StreamObserver<MigrateRegionResponse> responseObserver) {
        try {
            logger.info("MIGRATE_REGION operation: region={}, target={}, migrationId={}",
                request.getRegionId(), request.getTargetServer(), request.getMigrationId());

            if (!activeRegions.containsKey(request.getRegionId())) {
                responseObserver.onNext(MigrateRegionResponse.newBuilder()
                    .setAccepted(false)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_FOUND)
                    .setErrorMessage("Region not found: " + request.getRegionId())
                    .build());
                responseObserver.onCompleted();
                return;
            }

            responseObserver.onNext(MigrateRegionResponse.newBuilder()
                .setAccepted(true)
                .setErrorCode(ErrorCode.ERROR_OK)
                .build());

            logger.info("MIGRATE_REGION operation accepted: region={}", request.getRegionId());

        } catch (Exception e) {
            logger.error("MIGRATE_REGION operation failed", e);
            responseObserver.onNext(MigrateRegionResponse.newBuilder()
                .setAccepted(false)
                .setErrorCode(ErrorCode.ERROR_INTERNAL)
                .setErrorMessage(e.getMessage())
                .build());
        }
        responseObserver.onCompleted();
    }

    // Replica synchronization
    @Override
    public void getReplicationLog(GetReplicationLogRequest request, StreamObserver<ReplicationLogEntry> responseObserver) {
        try {
            logger.info("GET_REPLICATION_LOG operation: region={}, start={}, end={}",
                request.getRegionId(), request.getStartSequence(), request.getEndSequence());

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onError(new RuntimeException("Region not online: " + request.getRegionId()));
                return;
            }

            responseObserver.onCompleted();
            logger.info("GET_REPLICATION_LOG operation completed");

        } catch (Exception e) {
            logger.error("GET_REPLICATION_LOG operation failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void applyReplicationLog(ApplyReplicationLogRequest request, StreamObserver<ApplyReplicationLogResponse> responseObserver) {
        try {
            logger.info("APPLY_REPLICATION_LOG operation: region={}, logs={}",
                request.getRegionId(), request.getLogsCount());

            if (!isRegionOnline(request.getRegionId())) {
                responseObserver.onNext(ApplyReplicationLogResponse.newBuilder()
                    .setSuccess(false)
                    .setAppliedCount(0)
                    .setLastAppliedSequence(0)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
                responseObserver.onCompleted();
                return;
            }

            responseObserver.onNext(ApplyReplicationLogResponse.newBuilder()
                .setSuccess(true)
                .setAppliedCount(request.getLogsCount())
                .setLastAppliedSequence(request.getLogs(request.getLogsCount() - 1).getSequenceId())
                .setErrorCode(ErrorCode.ERROR_OK)
                .build());

            logger.info("APPLY_REPLICATION_LOG operation completed: applied={}", request.getLogsCount());

        } catch (Exception e) {
            logger.error("APPLY_REPLICATION_LOG operation failed", e);
            responseObserver.onNext(ApplyReplicationLogResponse.newBuilder()
                .setSuccess(false)
                .setAppliedCount(0)
                .setLastAppliedSequence(0)
                .setErrorCode(ErrorCode.ERROR_INTERNAL)
                .setErrorMessage(e.getMessage())
                .build());
        }
        responseObserver.onCompleted();
    }

    // Helper methods
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean isRegionOnline(String regionId) {
        return activeRegions.containsKey(regionId);
    }

    private void insertRow(String tableName, byte[] key, Map<String, byte[]> columns) throws SQLException {
        logger.debug("Inserting row: {}", bytesToHex(key));
    }

    private Map<String, byte[]> selectRow(String tableName, byte[] rowKey) throws SQLException {
        logger.debug("Selecting row: {}", bytesToHex(rowKey));
        return null; // Return null if not found
    }

    private boolean deleteRow(String tableName, byte[] rowKey) throws SQLException {
        logger.debug("Deleting row: {}", bytesToHex(rowKey));
        return true;
    }

    private boolean existsRow(String tableName, byte[] rowKey) throws SQLException {
        logger.debug("Checking existence of row: {}", bytesToHex(rowKey));
        return false;
    }

    private void batchInsert(String tableName, List<RowData> rows) throws SQLException {
        logger.debug("Batch inserting {} rows", rows.size());
    }

    private List<Map<String, byte[]>> batchSelect(String tableName, List<byte[]> rowKeys) throws SQLException {
        logger.debug("Batch selecting {} rows", rowKeys.size());
        return new ArrayList<>();
    }

    private int batchDelete(String tableName, List<byte[]> rowKeys) throws SQLException {
        logger.debug("Batch deleting {} rows", rowKeys.size());
        return rowKeys.size();
    }

    private void scanRows(ScanRequest request, StreamObserver<ScanResponse> responseObserver) {
        logger.debug("Scanning rows from {} to {}",
            bytesToHex(request.getStartKey().toByteArray()),
            bytesToHex(request.getEndKey().toByteArray()));
        responseObserver.onCompleted();
    }

    private long executeCountQuery(String tableName, String regionId, CountQuery countQuery) {
        logger.debug("Executing count query");
        return 0;
    }

    private AggregateResult executeAggregateQuery(String tableName, String regionId, AggregateQuery aggregateQuery) {
        logger.debug("Executing aggregate query");
        return AggregateResult.newBuilder().build();
    }

    private FilterResult executeFilterQuery(String tableName, String regionId, FilterQuery filterQuery) {
        logger.debug("Executing filter query");
        return FilterResult.newBuilder().build();
    }

    private void createRegionTable(RegionInfo region) {
        try {
            database.createRegionTable(region.getTableName());
        } catch (SQLException e) {
            logger.error("Failed to create region table: {}", region.getTableName(), e);
        }
    }

    private RegionStats getRegionStats(RegionInfo region) {
        try {
            MySQLDatabase.TableStats stats = database.getTableStats(region.getTableName());
            return new RegionStats(stats.sizeBytes, stats.rowCount);
        } catch (SQLException e) {
            logger.error("Failed to get region stats: {}", region.getTableName(), e);
            return new RegionStats(0, 0);
        }
    }

    private static class RegionStats {
        final long sizeBytes;
        final long rowCount;

        RegionStats(long sizeBytes, long rowCount) {
            this.sizeBytes = sizeBytes;
            this.rowCount = rowCount;
        }
    }

    /**
     * Convert Map<String, byte[]> to Map<String, ByteString>
     */
    private Map<String, ByteString> toByteStringMap(Map<String, byte[]> byteArrayMap) {
        Map<String, ByteString> result = new HashMap<>();
        if (byteArrayMap != null) {
            for (Map.Entry<String, byte[]> entry : byteArrayMap.entrySet()) {
                result.put(entry.getKey(), ByteString.copyFrom(entry.getValue()));
            }
        }
        return result;
    }
}
