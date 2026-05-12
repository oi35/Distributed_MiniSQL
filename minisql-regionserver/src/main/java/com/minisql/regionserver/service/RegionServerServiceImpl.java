package com.minisql.regionserver.service;

import com.google.protobuf.ByteString;
import com.minisql.regionserver.db.MySQLDatabase;
import com.minisql.regionserver.proto.*;
import com.minisql.regionserver.replication.PaxosAcceptor;
import com.minisql.regionserver.replication.PaxosProposer;
import com.minisql.regionserver.replication.PaxosTypes;
import com.minisql.regionserver.replication.ReplicationLogService;
import com.minisql.regionserver.replication.ReplicationManager;
import com.minisql.regionserver.replication.WalService;
import com.minisql.regionserver.store.InMemoryRegionDataStore;
import com.minisql.regionserver.store.RegionDataStore;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RegionInfo;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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

    private final WalService walService;
    private final ReplicationLogService replicationLogService;
    private final ReplicationManager replicationManager;
    private final PaxosProposer paxosProposer;
    private final Map<String, RegionDataStore> regionStores = new ConcurrentHashMap<>();

    public WalService getWalService() {
        return walService;
    }

    public ReplicationLogService getReplicationLogService() {
        return replicationLogService;
    }

    public ReplicationManager getReplicationManager() {
        return replicationManager;
    }

    public PaxosProposer getPaxosProposer() {
        return paxosProposer;
    }

    public RegionServerServiceImpl(String regionServerId) {
        this(regionServerId, loadDefaultProperties());
    }

    public RegionServerServiceImpl(String regionServerId, java.util.Properties properties) {
        this.regionServerId = regionServerId;

        // 支持内存后端，避免测试环境依赖MySQL
        boolean useMemoryBackend = "memory".equalsIgnoreCase(
                properties.getProperty("storage.backend", ""));
        if (useMemoryBackend) {
            this.database = null;
        } else {
            String jdbcUrl = properties.getProperty("mysql.url", "jdbc:mysql://localhost:3306/minisql");
            String username = properties.getProperty("mysql.username", "root");
            String password = properties.getProperty("mysql.password", "password");
            this.database = new MySQLDatabase(jdbcUrl, username, password);
        }

        // 支持禁用WAL（测试场景）
        boolean walDisabled = "false".equalsIgnoreCase(
                properties.getProperty("wal.enabled", "true"));
        if (walDisabled) {
            String walDir = properties.getProperty("wal.path",
                    Paths.get(System.getProperty("java.io.tmpdir"), "wal-test", regionServerId).toString());
            this.walService = new WalService(walDir, regionServerId);
        } else {
            String walDir = properties.getProperty("wal.dir",
                    Paths.get(System.getProperty("user.dir"), "wal", regionServerId).toString());
            this.walService = new WalService(walDir, regionServerId);
        }

        PaxosAcceptor sharedAcceptor = new PaxosAcceptor(regionServerId);
        this.replicationLogService = new ReplicationLogService(walService, sharedAcceptor);
        this.replicationManager = new ReplicationManager();
        this.paxosProposer = new PaxosProposer(regionServerId, sharedAcceptor);
        // 从WAL恢复未提交的数据
        recoverFromWal();
        logger.info("RegionServerServiceImpl initialized for {} with custom properties", regionServerId);
    }

    /**
     * 从WAL恢复数据 —— 启动时重放WAL中尚未持久化到存储的日志记录。
     *
     * 遍历所有WAL记录（包括归档文件），将每条记录应用到对应的RegionDataStore。
     * 恢复完成后，sequenceId从WAL的最新序列号恢复。
     */
    private void recoverFromWal() {
        try {
            List<com.minisql.regionserver.wal.WalRecord> allRecords = walService.loadAll();
            if (allRecords.isEmpty()) {
                logger.info("WAL恢复完成: 无待恢复记录");
                return;
            }

            int recovered = 0;
            for (com.minisql.regionserver.wal.WalRecord record : allRecords) {
                try {
                    // 对于每个region，确保有对应的data store
                    RegionDataStore store = regionStores.get(record.getRegionId());
                    if (store == null) {
                        store = new InMemoryRegionDataStore();
                        regionStores.put(record.getRegionId(), store);
                    }

                    // 应用记录到存储
                    if ("PUT".equalsIgnoreCase(record.getOperation())) {
                        Map<String, com.google.protobuf.ByteString> bsCols = new HashMap<>();
                        for (Map.Entry<String, byte[]> entry : record.getColumns().entrySet()) {
                            bsCols.put(entry.getKey(),
                                    com.google.protobuf.ByteString.copyFrom(entry.getValue()));
                        }
                        store.upsert(record.getKey(), bsCols, record.getTimestamp());
                    } else if ("DELETE".equalsIgnoreCase(record.getOperation())) {
                        store.delete(record.getKey());
                    }
                    recovered++;

                    // 更新序列号
                    if (record.getSequenceId() > sequenceId) {
                        sequenceId = record.getSequenceId();
                    }
                } catch (Exception e) {
                    logger.warn("WAL恢复单条记录失败: seq={}, region={}",
                            record.getSequenceId(), record.getRegionId(), e);
                }
            }

            logger.info("WAL恢复完成: {}/{} 条记录已恢复, 当前序列号={}",
                    recovered, allRecords.size(), sequenceId);
        } catch (Exception e) {
            logger.error("WAL恢复失败", e);
        }
    }

    public boolean put(String tableName, String regionId, String key, Map<String, byte[]> columns) {
        try {
            insertRow(regionId, tableName, key.getBytes(StandardCharsets.UTF_8), columns);
            return true;
        } catch (Exception e) {
            logger.error("Local PUT failed", e);
            return false;
        }
    }

    public Map<String, byte[]> get(String tableName, String regionId, String key) {
        try {
            Map<String, byte[]> columns = selectRow(regionId, tableName, key.getBytes(StandardCharsets.UTF_8));
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
        try {
            return deleteRow(regionId, tableName, key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Local DELETE failed", e);
            return false;
        }
    }

    public boolean exists(String tableName, String regionId, String key) {
        try {
            return existsRow(regionId, tableName, key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Local EXISTS failed", e);
            return false;
        }
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

    public boolean openRegion(RegionInfo region) {
        createRegionTable(region);
        activeRegions.put(region.getRegionId(), region);
        RegionDataStore store = regionStores.computeIfAbsent(region.getRegionId(), k -> new InMemoryRegionDataStore());
        replicationLogService.registerRegionStore(region.getRegionId(), store);
        if (region.getReplicaServersCount() > 0) {
            String primaryAddr = region.getPrimaryServer();
            if (primaryAddr == null || primaryAddr.isEmpty()) {
                primaryAddr = regionServerId;
            }
            replicationManager.setReplicasFromRegionInfo(
                    region.getRegionId(), primaryAddr, region.getReplicaServersList());
        }
        return true;
    }

    public boolean closeRegion(String regionId) {
        RegionInfo removed = activeRegions.remove(regionId);
        if (removed != null) {
            replicationLogService.unregisterRegionStore(regionId);
            regionStores.remove(regionId);
        }
        return removed != null;
    }

    public boolean migrateRegion(String regionId, String targetServer, String migrationId) {
        return activeRegions.containsKey(regionId);
    }

    public List<RegionDataStore.StoredRowRecord> listRows(String regionId, int limit) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) {
            return Collections.emptyList();
        }
        List<RegionDataStore.StoredRowRecord> rows = store.scan(null, null, false);
        if (limit > 0 && rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
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

            // WAL-first: write log before applying to store
            com.minisql.regionserver.wal.WalRecord walRecord = walService.append(
                    request.getRegionId(), request.getTableName(),
                    "PUT", request.getKey().toByteArray(), toByteArrayMap(request.getColumnsMap()));

            // Paxos consensus for strong consistency across replicas
            List<String> replicas = replicationManager.getReplicas(request.getRegionId());
            if (!replicas.isEmpty()) {
                PaxosTypes.ConsensusResult result = paxosProposer.proposeWithRetry(
                        request.getRegionId(), walRecord, replicas);
                if (result == PaxosTypes.ConsensusResult.COMMITTED) {
                    logger.info("Paxos 共识达成: region={}, seq={}",
                            request.getRegionId(), walRecord.getSequenceId());
                } else {
                    logger.warn("Paxos 共识失败({})，回退到异步复制: region={}, seq={}",
                            result, request.getRegionId(), walRecord.getSequenceId());
                }
            }

            // Apply to local store after WAL + consensus
            insertRow(request.getRegionId(), request.getTableName(), request.getKey().toByteArray(), toByteArrayMap(request.getColumnsMap()));

            // Async replication for eventual consistency
            replicationManager.replicate(request.getRegionId(), walRecord);

            responseObserver.onNext(PutResponse.newBuilder()
                .setSuccess(true)
                .setSequenceId(walRecord.getSequenceId())
                .setErrorCode(ErrorCode.ERROR_OK)
                .build());

            logger.info("PUT operation completed: seq={}", walRecord.getSequenceId());

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

            Map<String, byte[]> columns = selectRow(request.getRegionId(), request.getTableName(), request.getKey().toByteArray());

            if (columns != null && request.getColumnsCount() > 0) {
                Map<String, byte[]> filtered = new java.util.HashMap<>();
                for (String col : request.getColumnsList()) {
                    byte[] val = columns.get(col);
                    if (val != null) {
                        filtered.put(col, val);
                    }
                }
                columns = filtered;
            }

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

            boolean existed = deleteRow(request.getRegionId(), request.getTableName(), request.getKey().toByteArray());

            // Write WAL
            com.minisql.regionserver.wal.WalRecord walRecord = walService.append(
                    request.getRegionId(), request.getTableName(),
                    "DELETE", request.getKey().toByteArray(), new HashMap<>());

            // Paxos consensus with retry
            List<String> replicas = replicationManager.getReplicas(request.getRegionId());
            if (!replicas.isEmpty()) {
                PaxosTypes.ConsensusResult result = paxosProposer.proposeWithRetry(
                        request.getRegionId(), walRecord, replicas);
                logger.info("Paxos consensus for DELETE: region={}, seq={}, result={}",
                        request.getRegionId(), walRecord.getSequenceId(), result);
            }

            // Async replication as eventual consistency fallback
            replicationManager.replicate(request.getRegionId(), walRecord);

            responseObserver.onNext(DeleteResponse.newBuilder()
                .setSuccess(existed)
                .setExisted(existed)
                .setSequenceId(walRecord.getSequenceId())
                .setErrorCode(existed ? ErrorCode.ERROR_OK : ErrorCode.ERROR_NOT_FOUND)
                .setErrorMessage(existed ? "" : "Row not found")
                .build());

            logger.info("DELETE operation completed: existed={}, seq={}", existed, walRecord.getSequenceId());
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

            boolean exists = existsRow(request.getRegionId(), request.getTableName(), request.getKey().toByteArray());

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

            batchInsert(request.getRegionId(), request.getTableName(), request.getRowsList());

            // Write each row to WAL and collect records for replication
            List<com.minisql.regionserver.wal.WalRecord> walRecords = new ArrayList<>();
            List<Long> sequenceIds = new ArrayList<>();
            for (RowData row : request.getRowsList()) {
                com.minisql.regionserver.wal.WalRecord walRecord = walService.append(
                        request.getRegionId(), request.getTableName(),
                        "PUT", row.getKey().toByteArray(), toByteArrayMap(row.getColumnsMap()));
                walRecords.add(walRecord);
                sequenceIds.add(walRecord.getSequenceId());
            }

            // Batch replicate to all replicas
            replicationManager.batchReplicate(request.getRegionId(), walRecords);

            responseObserver.onNext(BatchPutResponse.newBuilder()
                .setSuccessCount(request.getRowsCount())
                .setFailedCount(0)
                .addAllSequenceIds(sequenceIds)
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
                Map<String, byte[]> columns = selectRow(request.getRegionId(), request.getTableName(), key.toByteArray());
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
                boolean deleted = deleteRow(request.getRegionId(), request.getTableName(), key.toByteArray());
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
                responseObserver.onCompleted();
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
            // Register a data store so that replicated logs can be applied
            regionStores.computeIfAbsent(regionId, k -> new InMemoryRegionDataStore());
            replicationLogService.registerRegionStore(regionId, regionStores.get(regionId));
            // Set up replica topology from Master's RegionInfo
            com.minisql.common.proto.RegionInfo region = request.getRegion();
            if (region.getReplicaServersCount() > 0) {
                String primaryAddr = region.getPrimaryServer();
                if (primaryAddr == null || primaryAddr.isEmpty()) {
                    primaryAddr = regionServerId;
                }
                replicationManager.setReplicasFromRegionInfo(
                        regionId, primaryAddr, region.getReplicaServersList());
            }
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
            if (removed != null) {
                replicationLogService.unregisterRegionStore(request.getRegionId());
                regionStores.remove(request.getRegionId());
            }

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

    // Replica synchronization — delegated to ReplicationLogService

    @Override
    public void getReplicationLog(GetReplicationLogRequest request,
                                   StreamObserver<ReplicationLogEntry> responseObserver) {
        replicationLogService.handleGetReplicationLog(request, responseObserver);
    }

    @Override
    public void applyReplicationLog(ApplyReplicationLogRequest request,
                                     StreamObserver<ApplyReplicationLogResponse> responseObserver) {
        replicationLogService.handleApplyReplicationLog(request, responseObserver);
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

    private void insertRow(String regionId, String tableName, byte[] key, Map<String, byte[]> columns) {
        RegionDataStore store = regionStores.computeIfAbsent(regionId, k -> new InMemoryRegionDataStore());
        Map<String, ByteString> bsCols = new HashMap<>();
        for (Map.Entry<String, byte[]> e : columns.entrySet()) {
            bsCols.put(e.getKey(), ByteString.copyFrom(e.getValue()));
        }
        store.upsert(key, bsCols, System.currentTimeMillis());
    }

    private Map<String, byte[]> selectRow(String regionId, String tableName, byte[] rowKey) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) return null;
        RegionDataStore.StoredRowRecord record = store.get(rowKey);
        if (record == null) return null;
        Map<String, byte[]> result = new HashMap<>();
        for (Map.Entry<String, ByteString> e : record.getColumns().entrySet()) {
            result.put(e.getKey(), e.getValue().toByteArray());
        }
        return result;
    }

    private boolean deleteRow(String regionId, String tableName, byte[] rowKey) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) return false;
        return store.delete(rowKey);
    }

    private boolean existsRow(String regionId, String tableName, byte[] rowKey) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) return false;
        return store.exists(rowKey);
    }

    private void batchInsert(String regionId, String tableName, List<RowData> rows) {
        RegionDataStore store = regionStores.computeIfAbsent(regionId, k -> new InMemoryRegionDataStore());
        for (RowData row : rows) {
            Map<String, ByteString> bsCols = new HashMap<>();
            for (Map.Entry<String, ByteString> e : row.getColumnsMap().entrySet()) {
                bsCols.put(e.getKey(), e.getValue());
            }
            store.upsert(row.getKey().toByteArray(), bsCols, System.currentTimeMillis());
        }
    }

    private List<Map<String, byte[]>> batchSelect(String regionId, String tableName, List<byte[]> rowKeys) {
        RegionDataStore store = regionStores.get(regionId);
        List<Map<String, byte[]>> results = new ArrayList<>();
        if (store == null) return results;
        for (byte[] key : rowKeys) {
            RegionDataStore.StoredRowRecord record = store.get(key);
            if (record != null) {
                Map<String, byte[]> row = new HashMap<>();
                for (Map.Entry<String, ByteString> e : record.getColumns().entrySet()) {
                    row.put(e.getKey(), e.getValue().toByteArray());
                }
                results.add(row);
            }
        }
        return results;
    }

    private int batchDelete(String regionId, String tableName, List<byte[]> rowKeys) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) return 0;
        int deleted = 0;
        for (byte[] key : rowKeys) {
            if (store.delete(key)) deleted++;
        }
        return deleted;
    }

    private void scanRows(ScanRequest request, StreamObserver<ScanResponse> responseObserver) {
        RegionDataStore store = regionStores.get(request.getRegionId());
        if (store == null) {
            responseObserver.onCompleted();
            return;
        }
        byte[] startKey = request.getStartKey().isEmpty() ? null : request.getStartKey().toByteArray();
        byte[] endKey = request.getEndKey().isEmpty() ? null : request.getEndKey().toByteArray();
        int limit = request.getLimit() > 0 ? request.getLimit() : Integer.MAX_VALUE;

        List<RegionDataStore.StoredRowRecord> allRows = store.scan(startKey, endKey, false);
        int sent = 0;
        for (RegionDataStore.StoredRowRecord record : allRows) {
            if (sent >= limit) break;
            ScanResponse.Builder builder = ScanResponse.newBuilder()
                    .setKey(ByteString.copyFrom(record.getKey()))
                    .setTimestamp(record.getTimestamp())
                    .setHasMore(sent + 1 < Math.min(allRows.size(), limit));
            builder.putAllColumns(record.getColumns());
            responseObserver.onNext(builder.build());
            sent++;
        }
        responseObserver.onCompleted();
    }

    private long executeCountQuery(String tableName, String regionId, CountQuery countQuery) {
        RegionDataStore store = regionStores.get(regionId);
        return store != null ? store.rowCount() : 0;
    }

    private AggregateResult executeAggregateQuery(String tableName, String regionId, AggregateQuery aggregateQuery) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) {
            return AggregateResult.newBuilder().setValue(0).setCount(0).build();
        }
        List<RegionDataStore.StoredRowRecord> allRows = store.scan(null, null, false);
        double result = 0;
        int count = 0;
        boolean hasValue = false;
        double minVal = Double.MAX_VALUE;
        double maxVal = -Double.MAX_VALUE;
        String colName = aggregateQuery.getColumnName();
        for (RegionDataStore.StoredRowRecord record : allRows) {
            ByteString val = record.getColumns().get(colName);
            if (val != null) {
                try {
                    double num = Double.parseDouble(val.toStringUtf8());
                    result += num;
                    count++;
                    hasValue = true;
                    if (num < minVal) minVal = num;
                    if (num > maxVal) maxVal = num;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        switch (aggregateQuery.getFunction()) {
            case AVG:
                return AggregateResult.newBuilder().setValue(hasValue ? result / count : 0).setCount(count).build();
            case MIN:
                return AggregateResult.newBuilder().setValue(hasValue ? minVal : 0).setCount(count).build();
            case MAX:
                return AggregateResult.newBuilder().setValue(hasValue ? maxVal : 0).setCount(count).build();
            case SUM:
            default:
                return AggregateResult.newBuilder().setValue(result).setCount(count).build();
        }
    }

    private FilterResult executeFilterQuery(String tableName, String regionId, FilterQuery filterQuery) {
        RegionDataStore store = regionStores.get(regionId);
        if (store == null) {
            return FilterResult.newBuilder().build();
        }
        // Simple filter: return all rows (full filter engine is future work)
        List<RegionDataStore.StoredRowRecord> allRows = store.scan(null, null, false);
        FilterResult.Builder resultBuilder = FilterResult.newBuilder();
        for (RegionDataStore.StoredRowRecord record : allRows) {
            RowData.Builder rowBuilder = RowData.newBuilder()
                    .setKey(ByteString.copyFrom(record.getKey()));
            rowBuilder.putAllColumns(record.getColumns());
            resultBuilder.addRows(rowBuilder);
        }
        return resultBuilder.build();
    }

    private void createRegionTable(RegionInfo region) {
        if (database == null) {
            return;
        }
        try {
            database.createRegionTable(region.getTableName());
        } catch (SQLException e) {
            logger.error("Failed to create region table: {}", region.getTableName(), e);
        }
    }

    private RegionStats getRegionStats(RegionInfo region) {
        if (database == null) {
            return new RegionStats(0, 0);
        }
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

    private static Properties loadDefaultProperties() {
        Properties props = new Properties();
        try (InputStream is = RegionServerServiceImpl.class.getClassLoader()
                .getResourceAsStream("regionserver.conf")) {
            if (is != null) {
                props.load(is);
            }
        } catch (java.io.IOException e) {
            logger.warn("Failed to load regionserver.conf, using memory backend defaults", e);
        }
        if (!props.containsKey("storage.backend")) {
            props.setProperty("storage.backend", "memory");
        }
        return props;
    }
}
