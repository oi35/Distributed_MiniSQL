package com.minisql.client;

import com.google.protobuf.ByteString;
import com.minisql.client.conn.ConnectionManager;
import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RouteEntry;
import com.minisql.regionserver.proto.RegionServerServiceGrpc;
import com.minisql.regionserver.proto.ScanRequest;
import com.minisql.regionserver.proto.ScanResponse;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ParallelScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ParallelScanner.class);

    private final ExecutorService executor;
    private final ConnectionManager connectionManager;

    public ParallelScanner(ExecutorService executor, ConnectionManager connectionManager) {
        this.executor = executor;
        this.connectionManager = connectionManager;
    }

    public List<MiniSQLClient.ScanRow> scanParallel(
            String table,
            List<RouteEntry> routes,
            ByteString startKey,
            ByteString endKey,
            int limit,
            List<String> columns,
            String filter) {

        int perRegionLimit = limit > 0 ? limit : 0;

        @SuppressWarnings("unchecked")
        CompletableFuture<List<MiniSQLClient.ScanRow>>[] futures = new CompletableFuture[routes.size()];

        for (int i = 0; i < routes.size(); i++) {
            RouteEntry route = routes.get(i);
            futures[i] = CompletableFuture.supplyAsync(() ->
                    scanOneRegion(table, route, startKey, endKey, perRegionLimit, columns, filter), executor);
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures);
        try {
            all.join();
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MiniSQLClientException) {
                throw (MiniSQLClientException) cause;
            }
            throw new MiniSQLClientException(
                    "parallel scan failed: " + cause.getMessage(),
                    ErrorCode.ERROR_UNAVAILABLE, cause);
        }

        List<MiniSQLClient.ScanRow> merged = new ArrayList<>();
        int remaining = limit > 0 ? limit : Integer.MAX_VALUE;
        for (CompletableFuture<List<MiniSQLClient.ScanRow>> future : futures) {
            if (remaining <= 0) break;
            List<MiniSQLClient.ScanRow> regionRows = future.join();
            for (MiniSQLClient.ScanRow row : regionRows) {
                if (remaining <= 0) break;
                merged.add(row);
                remaining--;
            }
        }

        LOG.debug("parallel scan {} across {} regions returned {} rows",
                table, routes.size(), merged.size());
        return merged;
    }

    private List<MiniSQLClient.ScanRow> scanOneRegion(
            String table,
            RouteEntry route,
            ByteString startKey,
            ByteString endKey,
            int limit,
            List<String> columns,
            String filter) {

        ScanRequest.Builder builder = ScanRequest.newBuilder()
                .setTableName(table)
                .setRegionId(route.getRegionId())
                .setStartKey(startKey)
                .setEndKey(endKey)
                .setLimit(limit);
        if (columns != null) {
            builder.addAllColumns(columns);
        }
        if (filter != null && !filter.isEmpty()) {
            builder.setFilter(filter);
        }

        ManagedChannel channel = connectionManager.getChannel(route.getPrimaryAddress());
        RegionServerServiceGrpc.RegionServerServiceBlockingStub stub =
                RegionServerServiceGrpc.newBlockingStub(channel);

        Iterator<ScanResponse> iter = stub.scan(builder.build());
        List<MiniSQLClient.ScanRow> rows = new ArrayList<>();
        while (iter.hasNext()) {
            ScanResponse resp = iter.next();
            rows.add(new MiniSQLClient.ScanRow(resp.getKey(), resp.getColumnsMap(), resp.getTimestamp()));
        }
        return rows;
    }
}
