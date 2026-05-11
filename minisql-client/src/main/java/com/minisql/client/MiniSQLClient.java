package com.minisql.client;

import com.google.protobuf.ByteString;
import com.minisql.client.conn.ConnectionManager;
import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.client.route.RouteCache;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RouteEntry;
import com.minisql.master.proto.ClientMasterServiceGrpc;
import com.minisql.regionserver.proto.DeleteRequest;
import com.minisql.regionserver.proto.DeleteResponse;
import com.minisql.regionserver.proto.ExistsRequest;
import com.minisql.regionserver.proto.ExistsResponse;
import com.minisql.regionserver.proto.GetRequest;
import com.minisql.regionserver.proto.GetResponse;
import com.minisql.regionserver.proto.PutRequest;
import com.minisql.regionserver.proto.PutResponse;
import com.minisql.regionserver.proto.RegionServerServiceGrpc;
import com.minisql.regionserver.proto.ScanRequest;
import com.minisql.regionserver.proto.ScanResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MiniSQLClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MiniSQLClient.class);

    private final ManagedChannel masterChannel;
    private final boolean ownsMasterChannel;
    private final ClientMasterServiceGrpc.ClientMasterServiceBlockingStub masterStub;
    private final RouteCache routeCache;
    private final ConnectionManager connectionManager;
    private final ExecutorService scanExecutor;
    private final boolean ownsScanExecutor;
    private final ParallelScanner parallelScanner;

    public static MiniSQLClient connect(String masterAddress) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(masterAddress)
                .usePlaintext()
                .build();
        return new MiniSQLClient(channel, true, new ConnectionManager(), null);
    }

    public MiniSQLClient(ManagedChannel masterChannel, ConnectionManager connectionManager) {
        this(masterChannel, false, connectionManager, null);
    }

    public MiniSQLClient(ManagedChannel masterChannel, ConnectionManager connectionManager,
                         ExecutorService scanExecutor) {
        this(masterChannel, false, connectionManager, scanExecutor);
    }

    private MiniSQLClient(ManagedChannel masterChannel,
                          boolean ownsMasterChannel,
                          ConnectionManager connectionManager,
                          ExecutorService scanExecutor) {
        this.masterChannel = Objects.requireNonNull(masterChannel, "masterChannel");
        this.ownsMasterChannel = ownsMasterChannel;
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager");
        this.masterStub = ClientMasterServiceGrpc.newBlockingStub(masterChannel);
        this.routeCache = new RouteCache(this.masterStub);
        if (scanExecutor != null) {
            this.scanExecutor = scanExecutor;
            this.ownsScanExecutor = false;
        } else {
            this.scanExecutor = defaultScanExecutor();
            this.ownsScanExecutor = true;
        }
        this.parallelScanner = new ParallelScanner(this.scanExecutor, this.connectionManager);
    }

    private static ExecutorService defaultScanExecutor() {
        int threads = Math.min(Runtime.getRuntime().availableProcessors() * 2, 16);
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "minisql-scan-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(threads, factory);
    }

    RouteCache routeCache() {
        return routeCache;
    }

    public ExecutorService scanExecutor() {
        return scanExecutor;
    }

    public PutResult put(String table, ByteString key, Map<String, ByteString> columns) {
        PutResponse response = callWithRouteRetry(table, key, route -> {
            PutRequest request = PutRequest.newBuilder()
                    .setTableName(table)
                    .setRegionId(route.getRegionId())
                    .setKey(key)
                    .putAllColumns(columns)
                    .setSyncReplicas(true)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            return regionStub(route).put(request);
        }, PutResponse::getErrorCode, PutResponse::getSuccess);

        return new PutResult(response.getSuccess(), response.getSequenceId(),
                response.getErrorCode(), response.getErrorMessage());
    }

    public GetResult get(String table, ByteString key, List<String> columns) {
        GetResponse response = callWithRouteRetry(table, key, route -> {
            GetRequest.Builder builder = GetRequest.newBuilder()
                    .setTableName(table)
                    .setRegionId(route.getRegionId())
                    .setKey(key);
            if (columns != null) {
                builder.addAllColumns(columns);
            }
            return regionStub(route).get(builder.build());
        }, GetResponse::getErrorCode, r -> true);

        return new GetResult(response.getFound(), response.getColumnsMap(),
                response.getTimestamp(), response.getErrorCode(), response.getErrorMessage());
    }

    public DeleteResult delete(String table, ByteString key) {
        DeleteResponse response = callWithRouteRetry(table, key, route -> {
            DeleteRequest request = DeleteRequest.newBuilder()
                    .setTableName(table)
                    .setRegionId(route.getRegionId())
                    .setKey(key)
                    .setSyncReplicas(true)
                    .build();
            return regionStub(route).delete(request);
        }, DeleteResponse::getErrorCode, DeleteResponse::getSuccess);

        return new DeleteResult(response.getSuccess(), response.getExisted(),
                response.getSequenceId(), response.getErrorCode(), response.getErrorMessage());
    }

    public boolean exists(String table, ByteString key) {
        ExistsResponse response = callWithRouteRetry(table, key, route -> {
            ExistsRequest request = ExistsRequest.newBuilder()
                    .setTableName(table)
                    .setRegionId(route.getRegionId())
                    .setKey(key)
                    .build();
            return regionStub(route).exists(request);
        }, r -> ErrorCode.ERROR_OK, r -> true);

        return response.getExists();
    }

    public List<ScanRow> scan(String table, ByteString startKey, ByteString endKey,
                              int limit, List<String> columns) {
        return scan(table, startKey, endKey, limit, columns, null);
    }

    public List<ScanRow> scan(String table, ByteString startKey, ByteString endKey,
                              int limit, List<String> columns, String filter) {
        List<RouteEntry> routes = routeCache.lookupRange(table, startKey, endKey);
        if (routes.isEmpty()) {
            return List.of();
        }
        if (routes.size() > 1) {
            return parallelScanner.scanParallel(table, routes, startKey, endKey, limit, columns, filter);
        }
        RouteEntry route = routes.get(0);
        ScanRequest.Builder builder = ScanRequest.newBuilder()
                .setTableName(table)
                .setRegionId(route.getRegionId())
                .setStartKey(startKey)
                .setEndKey(endKey)
                .setLimit(limit > 0 ? limit : 0);
        if (columns != null) {
            builder.addAllColumns(columns);
        }
        if (filter != null && !filter.isEmpty()) {
            builder.setFilter(filter);
        }
        Iterator<ScanResponse> iter = regionStub(route).scan(builder.build());
        List<ScanRow> collected = new ArrayList<>();
        int remaining = limit > 0 ? limit : Integer.MAX_VALUE;
        while (iter.hasNext() && remaining > 0) {
            ScanResponse row = iter.next();
            collected.add(new ScanRow(row.getKey(), row.getColumnsMap(), row.getTimestamp()));
            remaining--;
        }
        return collected;
    }

    private <T> T callWithRouteRetry(String table,
                                     ByteString key,
                                     RegionCall<T> call,
                                     ErrorCodeExtractor<T> errorCode,
                                     SuccessPredicate<T> success) {
        RouteEntry route = routeCache.lookup(table, key);
        T response;
        try {
            response = call.invoke(route);
        } catch (RuntimeException e) {
            routeCache.invalidate(table);
            throw new MiniSQLClientException(
                    "RPC to region " + route.getRegionId() + " failed",
                    ErrorCode.ERROR_UNAVAILABLE, e);
        }
        if (!success.test(response) && errorCode.extract(response) == ErrorCode.ERROR_STALE_ROUTE) {
            LOG.debug("stale route for {} key, refreshing cache and retrying once", table);
            routeCache.invalidate(table);
            RouteEntry refreshed = routeCache.lookup(table, key);
            response = call.invoke(refreshed);
        }
        return response;
    }

    private RegionServerServiceGrpc.RegionServerServiceBlockingStub regionStub(RouteEntry route) {
        ManagedChannel channel = connectionManager.getChannel(route.getPrimaryAddress());
        return RegionServerServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void close() {
        connectionManager.close();
        if (ownsScanExecutor) {
            scanExecutor.shutdown();
            try {
                if (!scanExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scanExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scanExecutor.shutdownNow();
            }
        }
        if (ownsMasterChannel) {
            masterChannel.shutdown();
        }
    }

    @FunctionalInterface
    private interface RegionCall<T> {
        T invoke(RouteEntry route);
    }

    @FunctionalInterface
    private interface ErrorCodeExtractor<T> {
        ErrorCode extract(T response);
    }

    @FunctionalInterface
    private interface SuccessPredicate<T> {
        boolean test(T response);
    }

    public static final class PutResult {
        private final boolean success;
        private final long sequenceId;
        private final ErrorCode errorCode;
        private final String errorMessage;

        PutResult(boolean success, long sequenceId, ErrorCode errorCode, String errorMessage) {
            this.success = success;
            this.sequenceId = sequenceId;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public long getSequenceId() { return sequenceId; }
        public ErrorCode getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static final class GetResult {
        private final boolean found;
        private final Map<String, ByteString> columns;
        private final long timestamp;
        private final ErrorCode errorCode;
        private final String errorMessage;

        GetResult(boolean found, Map<String, ByteString> columns, long timestamp,
                  ErrorCode errorCode, String errorMessage) {
            this.found = found;
            this.columns = columns;
            this.timestamp = timestamp;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public boolean isFound() { return found; }
        public Map<String, ByteString> getColumns() { return columns; }
        public long getTimestamp() { return timestamp; }
        public ErrorCode getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static final class DeleteResult {
        private final boolean success;
        private final boolean existed;
        private final long sequenceId;
        private final ErrorCode errorCode;
        private final String errorMessage;

        DeleteResult(boolean success, boolean existed, long sequenceId,
                     ErrorCode errorCode, String errorMessage) {
            this.success = success;
            this.existed = existed;
            this.sequenceId = sequenceId;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public boolean didExist() { return existed; }
        public long getSequenceId() { return sequenceId; }
        public ErrorCode getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static final class ScanRow {
        private final ByteString key;
        private final Map<String, ByteString> columns;
        private final long timestamp;

        ScanRow(ByteString key, Map<String, ByteString> columns, long timestamp) {
            this.key = key;
            this.columns = columns;
            this.timestamp = timestamp;
        }

        public ByteString getKey() { return key; }
        public Map<String, ByteString> getColumns() { return columns; }
        public long getTimestamp() { return timestamp; }
    }
}
