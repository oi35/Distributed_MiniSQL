package com.minisql.regionserver.service;

import com.google.protobuf.ByteString;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RegionInfo;
import com.minisql.common.proto.RegionState;
import com.minisql.regionserver.db.MySQLDatabase;
import com.minisql.regionserver.proto.AggregateQuery;
import com.minisql.regionserver.proto.AggregateResult;
import com.minisql.regionserver.proto.ApplyReplicationLogRequest;
import com.minisql.regionserver.proto.ApplyReplicationLogResponse;
import com.minisql.regionserver.proto.BatchDeleteRequest;
import com.minisql.regionserver.proto.BatchDeleteResponse;
import com.minisql.regionserver.proto.BatchGetRequest;
import com.minisql.regionserver.proto.BatchGetResponse;
import com.minisql.regionserver.proto.BatchPutRequest;
import com.minisql.regionserver.proto.BatchPutResponse;
import com.minisql.regionserver.proto.CloseRegionRequest;
import com.minisql.regionserver.proto.CloseRegionResponse;
import com.minisql.regionserver.proto.CountQuery;
import com.minisql.regionserver.proto.CountResult;
import com.minisql.regionserver.proto.DeleteRequest;
import com.minisql.regionserver.proto.DeleteResponse;
import com.minisql.regionserver.proto.ExistsRequest;
import com.minisql.regionserver.proto.ExistsResponse;
import com.minisql.regionserver.proto.FilterQuery;
import com.minisql.regionserver.proto.FilterResult;
import com.minisql.regionserver.proto.GetReplicationLogRequest;
import com.minisql.regionserver.proto.GetRequest;
import com.minisql.regionserver.proto.GetResponse;
import com.minisql.regionserver.proto.GetResult;
import com.minisql.regionserver.proto.MigrateRegionRequest;
import com.minisql.regionserver.proto.MigrateRegionResponse;
import com.minisql.regionserver.proto.OpenRegionRequest;
import com.minisql.regionserver.proto.OpenRegionResponse;
import com.minisql.regionserver.proto.PutRequest;
import com.minisql.regionserver.proto.PutResponse;
import com.minisql.regionserver.proto.QueryRequest;
import com.minisql.regionserver.proto.QueryResponse;
import com.minisql.regionserver.proto.RegionServerServiceGrpc;
import com.minisql.regionserver.proto.ReplicationLogEntry;
import com.minisql.regionserver.proto.RowData;
import com.minisql.regionserver.proto.ScanRequest;
import com.minisql.regionserver.proto.ScanResponse;
import com.minisql.regionserver.store.InMemoryRegionDataStore;
import com.minisql.regionserver.store.MySqlRegionDataStore;
import com.minisql.regionserver.store.RegionDataStore;
import com.minisql.regionserver.wal.WalManager;
import com.minisql.regionserver.wal.WalRecord;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RegionServer service implementation backed by pluggable storage and optional WAL replay.
 */
public class RegionServerServiceImpl extends RegionServerServiceGrpc.RegionServerServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(RegionServerServiceImpl.class);

    private final String regionServerId;
    private final MySQLDatabase database;
    private final String storageBackend;
    private final WalManager walManager;
    private final Map<String, RegionInfo> activeRegions = new ConcurrentHashMap<>();
    private final Map<String, RegionDataStore> regionStores = new ConcurrentHashMap<>();
    private final AtomicLong sequenceId = new AtomicLong();

    public RegionServerServiceImpl(String regionServerId) {
        this(regionServerId, new Properties());
    }

    public RegionServerServiceImpl(String regionServerId, Properties properties) {
        this.regionServerId = regionServerId;
        this.storageBackend = properties.getProperty("storage.backend", "mysql").trim().toLowerCase();

        if ("mysql".equals(storageBackend)) {
            String jdbcUrl = properties.getProperty("mysql.url", "jdbc:mysql://localhost:3306/minisql");
            String username = properties.getProperty("mysql.username", "root");
            String password = properties.getProperty("mysql.password", "password");
            this.database = new MySQLDatabase(jdbcUrl, username, password);
        } else {
            this.database = null;
        }

        boolean walEnabled = Boolean.parseBoolean(properties.getProperty("wal.enabled", "false"));
        if (walEnabled) {
            String walPath = properties.getProperty("wal.path", "target/wal");
            this.walManager = new WalManager(Paths.get(walPath), regionServerId);
            replayWal();
        } else {
            this.walManager = null;
        }

        logger.info("RegionServerServiceImpl initialized for {} using {} backend", regionServerId, storageBackend);
    }

    public boolean put(String tableName, String regionId, String key, Map<String, byte[]> columns) {
        try {
            ensureLocalRegion(regionId, tableName);
            long nextSequence = nextSequenceId();
            upsertRow(tableName, regionId, key.getBytes(StandardCharsets.UTF_8), toByteStringMap(columns), nextSequence);
            return true;
        } catch (Exception e) {
            logger.error("Local PUT failed", e);
            return false;
        }
    }

    public Map<String, byte[]> get(String tableName, String regionId, String key) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) {
            return null;
        }
        RegionDataStore.StoredRowRecord record = store.get(key.getBytes(StandardCharsets.UTF_8));
        return record == null ? null : toByteArrayMap(record.getColumns());
    }

    public boolean delete(String tableName, String regionId, String key) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) {
            return false;
        }
        boolean deleted = store.delete(key.getBytes(StandardCharsets.UTF_8));
        if (deleted) {
            appendWal(new WalRecord(nextSequenceId(), regionId, tableName, System.currentTimeMillis(),
                    "DELETE", key.getBytes(StandardCharsets.UTF_8), Collections.emptyMap()));
        }
        return deleted;
    }

    public boolean exists(String tableName, String regionId, String key) {
        RegionDataStore store = regionStores.get(regionId);
        return store != null && store.exists(key.getBytes(StandardCharsets.UTF_8));
    }

    public Set<String> getActiveRegionIds() {
        return Collections.unmodifiableSet(activeRegions.keySet());
    }

    public long getTotalSizeBytes() {
        long total = 0L;
        for (RegionDataStore store : regionStores.values()) {
            total += store.sizeBytes();
        }
        return total;
    }

    public long getTotalRowCount() {
        long total = 0L;
        for (RegionDataStore store : regionStores.values()) {
            total += store.rowCount();
        }
        return total;
    }

    public boolean openRegion(RegionInfo region) {
        ensureRegionOnline(region);
        return true;
    }

    public boolean closeRegion(String regionId) {
        return activeRegions.remove(regionId) != null;
    }

    public boolean migrateRegion(String regionId, String targetServer, String migrationId) {
        return activeRegions.containsKey(regionId);
    }

    public List<RegionDataStore.StoredRowRecord> listRows(String regionId, int limit) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) {
            return Collections.emptyList();
        }
        List<RegionDataStore.StoredRowRecord> rows = store.scan(new byte[0], new byte[0], false);
        if (limit > 0 && rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
    }

    @Override
    public void put(PutRequest request, StreamObserver<PutResponse> responseObserver) {
        if (!isRegionOnline(request.getRegionId())) {
            respondAndComplete(responseObserver, PutResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
            return;
        }

        long nextSequence = nextSequenceId();
        try {
            upsertRow(request.getTableName(), request.getRegionId(), request.getKey().toByteArray(),
                    request.getColumnsMap(), nextSequence);
            respondAndComplete(responseObserver, PutResponse.newBuilder()
                    .setSuccess(true)
                    .setErrorCode(ErrorCode.ERROR_OK)
                    .setSequenceId(nextSequence)
                    .build());
        } catch (Exception e) {
            logger.error("PUT operation failed", e);
            respondAndComplete(responseObserver, PutResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage(e.getMessage())
                    .build());
        }
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        if (!isRegionOnline(request.getRegionId())) {
            respondAndComplete(responseObserver, GetResponse.newBuilder()
                    .setFound(false)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
            return;
        }

        RegionDataStore store = regionStores.get(request.getRegionId());
        RegionDataStore.StoredRowRecord record = store == null ? null : store.get(request.getKey().toByteArray());

        GetResponse.Builder builder = GetResponse.newBuilder()
                .setFound(record != null)
                .setTimestamp(record == null ? System.currentTimeMillis() : record.getTimestamp())
                .setErrorCode(ErrorCode.ERROR_OK);
        if (record != null) {
            builder.putAllColumns(filterColumns(record.getColumns(), request.getColumnsList()));
        }
        respondAndComplete(responseObserver, builder.build());
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<DeleteResponse> responseObserver) {
        if (!isRegionOnline(request.getRegionId())) {
            respondAndComplete(responseObserver, DeleteResponse.newBuilder()
                    .setSuccess(false)
                    .setExisted(false)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
            return;
        }

        RegionDataStore store = regionStores.get(request.getRegionId());
        boolean deleted = store != null && store.delete(request.getKey().toByteArray());
        long nextSequence = deleted ? nextSequenceId() : sequenceId.get();
        if (deleted) {
            appendWal(new WalRecord(nextSequence, request.getRegionId(), request.getTableName(),
                    System.currentTimeMillis(), "DELETE", request.getKey().toByteArray(), Collections.emptyMap()));
        }

        respondAndComplete(responseObserver, DeleteResponse.newBuilder()
                .setSuccess(deleted)
                .setExisted(deleted)
                .setErrorCode(deleted ? ErrorCode.ERROR_OK : ErrorCode.ERROR_NOT_FOUND)
                .setErrorMessage(deleted ? "" : "Row not found")
                .setSequenceId(nextSequence)
                .build());
    }

    @Override
    public void exists(ExistsRequest request, StreamObserver<ExistsResponse> responseObserver) {
        RegionDataStore store = regionStores.get(request.getRegionId());
        respondAndComplete(responseObserver, ExistsResponse.newBuilder()
                .setExists(store != null && store.exists(request.getKey().toByteArray()))
                .build());
    }

    @Override
    public void batchPut(BatchPutRequest request, StreamObserver<BatchPutResponse> responseObserver) {
        if (!isRegionOnline(request.getRegionId())) {
            respondAndComplete(responseObserver, BatchPutResponse.newBuilder()
                    .setSuccessCount(0)
                    .setFailedCount(request.getRowsCount())
                    .addAllFailedIndices(getAllIndices(request.getRowsCount()))
                    .build());
            return;
        }

        List<Long> sequenceIds = new ArrayList<>();
        try {
            for (RowData row : request.getRowsList()) {
                long nextSequence = nextSequenceId();
                upsertRow(request.getTableName(), request.getRegionId(), row.getKey().toByteArray(),
                        row.getColumnsMap(), nextSequence);
                sequenceIds.add(nextSequence);
            }
            respondAndComplete(responseObserver, BatchPutResponse.newBuilder()
                    .setSuccessCount(request.getRowsCount())
                    .setFailedCount(0)
                    .addAllSequenceIds(sequenceIds)
                    .build());
        } catch (Exception e) {
            logger.error("BATCH_PUT operation failed", e);
            respondAndComplete(responseObserver, BatchPutResponse.newBuilder()
                    .setSuccessCount(sequenceIds.size())
                    .setFailedCount(request.getRowsCount() - sequenceIds.size())
                    .addAllFailedIndices(getAllIndicesFrom(sequenceIds.size(), request.getRowsCount()))
                    .addErrorMessages(e.getMessage())
                    .build());
        }
    }

    @Override
    public void batchGet(BatchGetRequest request, StreamObserver<BatchGetResponse> responseObserver) {
        List<GetResult> results = new ArrayList<>();
        RegionDataStore store = regionStores.get(request.getRegionId());
        if (store != null) {
            for (ByteString key : request.getKeysList()) {
                RegionDataStore.StoredRowRecord record = store.get(key.toByteArray());
                GetResult.Builder builder = GetResult.newBuilder()
                        .setFound(record != null)
                        .setTimestamp(record == null ? System.currentTimeMillis() : record.getTimestamp());
                if (record != null) {
                    builder.putAllColumns(filterColumns(record.getColumns(), request.getColumnsList()));
                }
                results.add(builder.build());
            }
        }
        respondAndComplete(responseObserver, BatchGetResponse.newBuilder().addAllResults(results).build());
    }

    @Override
    public void batchDelete(BatchDeleteRequest request, StreamObserver<BatchDeleteResponse> responseObserver) {
        RegionDataStore store = regionStores.get(request.getRegionId());
        if (store == null) {
            respondAndComplete(responseObserver, BatchDeleteResponse.newBuilder().build());
            return;
        }

        int deletedCount = 0;
        List<Boolean> results = new ArrayList<>();
        for (ByteString key : request.getKeysList()) {
            boolean deleted = store.delete(key.toByteArray());
            results.add(deleted);
            if (deleted) {
                deletedCount++;
                appendWal(new WalRecord(nextSequenceId(), request.getRegionId(), request.getTableName(),
                        System.currentTimeMillis(), "DELETE", key.toByteArray(), Collections.emptyMap()));
            }
        }

        respondAndComplete(responseObserver, BatchDeleteResponse.newBuilder()
                .setDeletedCount(deletedCount)
                .addAllResults(results)
                .build());
    }

    @Override
    public void scan(ScanRequest request, StreamObserver<ScanResponse> responseObserver) {
        if (!isRegionOnline(request.getRegionId())) {
            responseObserver.onError(new IllegalStateException("Region not online: " + request.getRegionId()));
            return;
        }

        RegionDataStore store = regionStores.get(request.getRegionId());
        List<RegionDataStore.StoredRowRecord> rows = store == null
                ? Collections.emptyList()
                : store.scan(request.getStartKey().toByteArray(), request.getEndKey().toByteArray(), request.getReverse());

        int total = rows.size();
        int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), total) : total;
        boolean hasExtra = limit < total;

        for (int i = 0; i < limit; i++) {
            RegionDataStore.StoredRowRecord record = rows.get(i);
            boolean hasMore = i < limit - 1 || hasExtra;
            responseObserver.onNext(ScanResponse.newBuilder()
                    .setKey(ByteString.copyFrom(record.getKey()))
                    .putAllColumns(filterColumns(record.getColumns(), request.getColumnsList()))
                    .setTimestamp(record.getTimestamp())
                    .setHasMore(hasMore)
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void query(QueryRequest request, StreamObserver<QueryResponse> responseObserver) {
        if (!isRegionOnline(request.getRegionId())) {
            respondAndComplete(responseObserver, QueryResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
            return;
        }

        try {
            QueryResponse.Builder response = QueryResponse.newBuilder()
                    .setSuccess(true)
                    .setErrorCode(ErrorCode.ERROR_OK);
            if (request.hasCount()) {
                response.setCountResult(executeCountQuery(request.getRegionId(), request.getCount()));
            } else if (request.hasAggregate()) {
                response.setAggregateResult(executeAggregateQuery(request.getRegionId(), request.getAggregate()));
            } else if (request.hasFilter()) {
                response.setFilterResult(executeFilterQuery(request.getRegionId(), request.getFilter()));
            }
            respondAndComplete(responseObserver, response.build());
        } catch (Exception e) {
            logger.error("QUERY operation failed", e);
            respondAndComplete(responseObserver, QueryResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage(e.getMessage())
                    .build());
        }
    }

    @Override
    public void openRegion(OpenRegionRequest request, StreamObserver<OpenRegionResponse> responseObserver) {
        try {
            RegionInfo region = ensureRegionOnline(request.getRegion());
            RegionDataStore store = regionStores.get(region.getRegionId());
            respondAndComplete(responseObserver, OpenRegionResponse.newBuilder()
                    .setSuccess(true)
                    .setErrorCode(ErrorCode.ERROR_OK)
                    .setSizeBytes(store == null ? 0 : store.sizeBytes())
                    .setRowCount(store == null ? 0 : store.rowCount())
                    .build());
        } catch (Exception e) {
            logger.error("OPEN_REGION operation failed", e);
            respondAndComplete(responseObserver, OpenRegionResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage(e.getMessage())
                    .build());
        }
    }

    @Override
    public void closeRegion(CloseRegionRequest request, StreamObserver<CloseRegionResponse> responseObserver) {
        boolean removed = closeRegion(request.getRegionId());
        respondAndComplete(responseObserver, CloseRegionResponse.newBuilder()
                .setSuccess(removed)
                .setErrorCode(removed ? ErrorCode.ERROR_OK : ErrorCode.ERROR_REGION_NOT_FOUND)
                .setErrorMessage(removed ? "" : "Region not found")
                .build());
    }

    @Override
    public void migrateRegion(MigrateRegionRequest request, StreamObserver<MigrateRegionResponse> responseObserver) {
        boolean exists = migrateRegion(request.getRegionId(), request.getTargetServer(), request.getMigrationId());
        respondAndComplete(responseObserver, MigrateRegionResponse.newBuilder()
                .setAccepted(exists)
                .setErrorCode(exists ? ErrorCode.ERROR_OK : ErrorCode.ERROR_REGION_NOT_FOUND)
                .setErrorMessage(exists ? "" : "Region not found: " + request.getRegionId())
                .build());
    }

    @Override
    public void getReplicationLog(GetReplicationLogRequest request, StreamObserver<ReplicationLogEntry> responseObserver) {
        if (walManager == null) {
            responseObserver.onCompleted();
            return;
        }

        for (WalRecord record : walManager.loadAll()) {
            if (!request.getRegionId().isEmpty() && !request.getRegionId().equals(record.getRegionId())) {
                continue;
            }
            if (record.getSequenceId() < request.getStartSequence()) {
                continue;
            }
            if (request.getEndSequence() > 0 && record.getSequenceId() > request.getEndSequence()) {
                continue;
            }
            responseObserver.onNext(ReplicationLogEntry.newBuilder()
                    .setSequenceId(record.getSequenceId())
                    .setRegionId(record.getRegionId())
                    .setTimestamp(record.getTimestamp())
                    .setOperation("DELETE".equals(record.getOperation())
                            ? ReplicationLogEntry.OperationType.DELETE
                            : ReplicationLogEntry.OperationType.PUT)
                    .setKey(ByteString.copyFrom(record.getKey()))
                    .putAllColumns(toByteStringMap(record.getColumns()))
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void applyReplicationLog(ApplyReplicationLogRequest request, StreamObserver<ApplyReplicationLogResponse> responseObserver) {
        if (!isRegionOnline(request.getRegionId())) {
            respondAndComplete(responseObserver, ApplyReplicationLogResponse.newBuilder()
                    .setSuccess(false)
                    .setAppliedCount(0)
                    .setLastAppliedSequence(0)
                    .setErrorCode(ErrorCode.ERROR_REGION_NOT_ONLINE)
                    .setErrorMessage("Region not online: " + request.getRegionId())
                    .build());
            return;
        }

        RegionInfo region = activeRegions.get(request.getRegionId());
        RegionDataStore store = regionStores.get(request.getRegionId());
        long lastAppliedSequence = 0;

        for (ReplicationLogEntry log : request.getLogsList()) {
            if (log.getOperation() == ReplicationLogEntry.OperationType.DELETE) {
                store.delete(log.getKey().toByteArray());
            } else {
                store.upsert(log.getKey().toByteArray(), log.getColumnsMap(), log.getTimestamp());
            }
            lastAppliedSequence = Math.max(lastAppliedSequence, log.getSequenceId());
            sequenceId.accumulateAndGet(log.getSequenceId(), Math::max);
            if (region == null) {
                activeRegions.put(request.getRegionId(), defaultRegionInfo(request.getRegionId(), request.getRegionId()));
            }
        }

        respondAndComplete(responseObserver, ApplyReplicationLogResponse.newBuilder()
                .setSuccess(true)
                .setAppliedCount(request.getLogsCount())
                .setLastAppliedSequence(lastAppliedSequence)
                .setErrorCode(ErrorCode.ERROR_OK)
                .build());
    }

    private void replayWal() {
        for (WalRecord record : walManager.loadAll()) {
            activeRegions.computeIfAbsent(record.getRegionId(),
                    ignored -> defaultRegionInfo(record.getRegionId(), record.getTableName()));
            RegionDataStore store = ensureRegionStore(record.getRegionId(), record.getTableName());
            if ("DELETE".equals(record.getOperation())) {
                store.delete(record.getKey());
            } else {
                store.upsert(record.getKey(), toByteStringMap(record.getColumns()), record.getTimestamp());
            }
            sequenceId.accumulateAndGet(record.getSequenceId(), Math::max);
        }
    }

    private void upsertRow(String tableName, String regionId, byte[] key, Map<String, ByteString> columns, long nextSequence) {
        RegionDataStore store = ensureRegionStore(regionId, tableName);
        store.upsert(key, columns, System.currentTimeMillis());
        appendWal(new WalRecord(nextSequence, regionId, tableName, System.currentTimeMillis(),
                "PUT", key, toByteArrayMap(columns)));
    }

    private RegionInfo ensureRegionOnline(RegionInfo region) {
        RegionInfo normalized = region.getState() == RegionState.REGION_ONLINE
                ? region
                : region.toBuilder().setState(RegionState.REGION_ONLINE).build();
        activeRegions.put(normalized.getRegionId(), normalized);
        ensureRegionStore(normalized.getRegionId(), normalized.getTableName());
        return normalized;
    }

    private void ensureLocalRegion(String regionId, String tableName) {
        activeRegions.computeIfAbsent(regionId, ignored -> defaultRegionInfo(regionId, tableName));
        ensureRegionStore(regionId, tableName);
    }

    private RegionInfo defaultRegionInfo(String regionId, String tableName) {
        return RegionInfo.newBuilder()
                .setRegionId(regionId)
                .setTableName(tableName)
                .setState(RegionState.REGION_ONLINE)
                .build();
    }

    private RegionDataStore ensureRegionStore(String regionId, String tableName) {
        return regionStores.computeIfAbsent(regionId, ignored -> createStore(regionId));
    }

    private RegionDataStore createStore(String regionId) {
        if ("mysql".equals(storageBackend)) {
            return new MySqlRegionDataStore(database, regionId);
        }
        return new InMemoryRegionDataStore();
    }

    private boolean isRegionOnline(String regionId) {
        return activeRegions.containsKey(regionId);
    }

    private CountResult executeCountQuery(String regionId, CountQuery query) {
        return CountResult.newBuilder()
                .setCount(scanRegion(regionId, query.getStartKey().toByteArray(), query.getEndKey().toByteArray(), false).size())
                .build();
    }

    private AggregateResult executeAggregateQuery(String regionId, AggregateQuery query) {
        List<RegionDataStore.StoredRowRecord> rows = scanRegion(regionId, query.getStartKey().toByteArray(),
                query.getEndKey().toByteArray(), false);

        double resultValue = 0D;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int count = 0;
        for (RegionDataStore.StoredRowRecord row : rows) {
            ByteString value = row.getColumns().get(query.getColumnName());
            if (value == null) {
                continue;
            }
            double numeric = Double.parseDouble(value.toStringUtf8());
            count++;
            resultValue += numeric;
            min = Math.min(min, numeric);
            max = Math.max(max, numeric);
        }

        double finalValue;
        switch (query.getFunction()) {
            case AVG:
                finalValue = count == 0 ? 0D : resultValue / count;
                break;
            case MIN:
                finalValue = count == 0 ? 0D : min;
                break;
            case MAX:
                finalValue = count == 0 ? 0D : max;
                break;
            case SUM:
            default:
                finalValue = resultValue;
                break;
        }

        return AggregateResult.newBuilder()
                .setValue(finalValue)
                .setCount(count)
                .build();
    }

    private FilterResult executeFilterQuery(String regionId, FilterQuery query) {
        List<RowData> rows = new ArrayList<>();
        List<RegionDataStore.StoredRowRecord> scanned = scanRegion(regionId, query.getStartKey().toByteArray(),
                query.getEndKey().toByteArray(), false);
        int limit = query.getLimit() > 0 ? query.getLimit() : scanned.size();
        for (int i = 0; i < scanned.size() && i < limit; i++) {
            RegionDataStore.StoredRowRecord record = scanned.get(i);
            rows.add(RowData.newBuilder()
                    .setKey(ByteString.copyFrom(record.getKey()))
                    .putAllColumns(filterColumns(record.getColumns(), query.getColumnsList()))
                    .build());
        }
        return FilterResult.newBuilder().addAllRows(rows).build();
    }

    private List<RegionDataStore.StoredRowRecord> scanRegion(String regionId, byte[] startKey, byte[] endKey, boolean reverse) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) {
            return Collections.emptyList();
        }
        return store.scan(startKey, endKey, reverse);
    }

    private void appendWal(WalRecord record) {
        if (walManager != null) {
            walManager.append(record);
        }
    }

    private long nextSequenceId() {
        return sequenceId.incrementAndGet();
    }

    private Map<String, ByteString> filterColumns(Map<String, ByteString> columns, List<String> requestedColumns) {
        if (requestedColumns == null || requestedColumns.isEmpty()) {
            return new LinkedHashMap<>(columns);
        }
        Map<String, ByteString> filtered = new LinkedHashMap<>();
        for (String requestedColumn : requestedColumns) {
            ByteString value = columns.get(requestedColumn);
            if (value != null) {
                filtered.put(requestedColumn, value);
            }
        }
        return filtered;
    }

    private Map<String, byte[]> toByteArrayMap(Map<String, ByteString> columns) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        if (columns == null) {
            return result;
        }
        for (Map.Entry<String, ByteString> entry : columns.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toByteArray());
        }
        return result;
    }

    private Map<String, ByteString> toByteStringMap(Map<String, byte[]> byteArrayMap) {
        Map<String, ByteString> result = new LinkedHashMap<>();
        if (byteArrayMap == null) {
            return result;
        }
        for (Map.Entry<String, byte[]> entry : byteArrayMap.entrySet()) {
            result.put(entry.getKey(), ByteString.copyFrom(entry.getValue()));
        }
        return result;
    }

    private List<Integer> getAllIndices(int count) {
        return getAllIndicesFrom(0, count);
    }

    private List<Integer> getAllIndicesFrom(int startInclusive, int endExclusive) {
        List<Integer> indices = new ArrayList<>();
        for (int i = startInclusive; i < endExclusive; i++) {
            indices.add(i);
        }
        return indices;
    }

    private <T> void respondAndComplete(StreamObserver<T> responseObserver, T response) {
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
