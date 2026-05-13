package com.minisql.client.benchmark;

import com.google.protobuf.ByteString;
import com.minisql.client.MiniSQLClient;
import com.minisql.client.conn.ConnectionManager;
import com.minisql.common.proto.RegionRouteTable;
import com.minisql.common.proto.RouteEntry;
import com.minisql.master.proto.ClientMasterServiceGrpc;
import com.minisql.master.proto.GetRouteTableRequest;
import com.minisql.master.proto.GetRouteTableResponse;
import com.minisql.regionserver.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能基准测试：测试 CRUD 操作的吞吐量和延迟。
 *
 * 测试方法：
 * - 使用 in-process gRPC 模拟 Master 和 RegionServer
 * - 对每种操作运行指定次数，测量总耗时
 * - 计算吞吐量 (ops/sec) 和平均延迟
 *
 * 运行方式：mvn test -Dtest=CrudBenchmarkTest -pl minisql-client
 */
public class CrudBenchmarkTest {

    private static final Logger LOG = LoggerFactory.getLogger(CrudBenchmarkTest.class);

    private static final int WARMUP_COUNT = 500;
    private static final int BENCHMARK_COUNT = 2000;

    @Rule
    public final GrpcCleanupRule cleanup = new GrpcCleanupRule();

    private FakeMaster master;
    private FakeRegionServer rs;
    private MiniSQLClient client;

    @Before
    public void setUp() throws Exception {
        master = new FakeMaster();
        rs = new FakeRegionServer();

        String masterName = InProcessServerBuilder.generateName();
        cleanup.register(InProcessServerBuilder.forName(masterName).directExecutor()
                .addService(master).build().start());

        String rsName = "rs-1:8001";
        cleanup.register(InProcessServerBuilder.forName(rsName).directExecutor()
                .addService(rs).build().start());

        ManagedChannel masterChannel = cleanup.register(
                InProcessChannelBuilder.forName(masterName).directExecutor().build());

        ConnectionManager connectionManager = new ConnectionManager(
                address -> cleanup.register(
                        InProcessChannelBuilder.forName(address).directExecutor().build()));

        client = new MiniSQLClient(masterChannel, connectionManager);
        master.setTable(singleRegionTable());
    }

    @After
    public void tearDown() {
        // GrpcCleanupRule handles cleanup
    }

    @Test
    public void benchmarkPutThroughput() {
        List<byte[]> keys = generateKeys(BENCHMARK_COUNT);
        warmupPuts(keys.subList(0, WARMUP_COUNT));

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_COUNT; i++) {
            client.put("t", ByteString.copyFrom(keys.get(i)),
                    Map.of("name", ByteString.copyFromUtf8("user-" + i),
                           "email", ByteString.copyFromUtf8("user" + i + "@test.com")));
        }
        long elapsed = System.nanoTime() - start;

        reportResult("PUT", BENCHMARK_COUNT, elapsed);
    }

    @Test
    public void benchmarkGetThroughput() {
        preloadData(BENCHMARK_COUNT);
        List<byte[]> keys = generateKeys(BENCHMARK_COUNT);
        for (int i = 0; i < WARMUP_COUNT; i++) {
            client.get("t", ByteString.copyFrom(keys.get(i)), null);
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_COUNT; i++) {
            client.get("t", ByteString.copyFrom(keys.get(i)), null);
        }
        long elapsed = System.nanoTime() - start;

        reportResult("GET", BENCHMARK_COUNT, elapsed);
    }

    @Test
    public void benchmarkDeleteThroughput() {
        preloadData(BENCHMARK_COUNT);
        List<byte[]> keys = generateKeys(BENCHMARK_COUNT);
        for (int i = 0; i < WARMUP_COUNT; i++) {
            client.delete("t", ByteString.copyFrom(keys.get(i)));
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_COUNT; i++) {
            client.delete("t", ByteString.copyFrom(keys.get(i)));
        }
        long elapsed = System.nanoTime() - start;

        reportResult("DELETE", BENCHMARK_COUNT, elapsed);
    }

    @Test
    public void benchmarkExistsThroughput() {
        preloadData(BENCHMARK_COUNT);
        List<byte[]> keys = generateKeys(BENCHMARK_COUNT);
        for (int i = 0; i < WARMUP_COUNT; i++) {
            client.exists("t", ByteString.copyFrom(keys.get(i)));
        }

        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_COUNT; i++) {
            client.exists("t", ByteString.copyFrom(keys.get(i)));
        }
        long elapsed = System.nanoTime() - start;

        reportResult("EXISTS", BENCHMARK_COUNT, elapsed);
    }

    // --- helpers ---

    private void warmupPuts(List<byte[]> keys) {
        for (int i = 0; i < keys.size(); i++) {
            client.put("t", ByteString.copyFrom(keys.get(i)),
                    Map.of("v", ByteString.copyFromUtf8("warmup")));
        }
    }

    private void preloadData(int count) {
        for (int i = 0; i < count; i++) {
            byte[] key = String.format("key-%06d", i).getBytes();
            rs.store.put(ByteString.copyFrom(key), Map.of("v", ByteString.copyFromUtf8("val-" + i)));
        }
    }

    private List<byte[]> generateKeys(int count) {
        List<byte[]> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(String.format("key-%06d", i).getBytes());
        }
        return keys;
    }

    private void reportResult(String operation, int count, long elapsedNanos) {
        double elapsedSec = elapsedNanos / 1_000_000_000.0;
        double throughput = count / elapsedSec;
        double avgLatencyUs = elapsedNanos / (double) count / 1000.0;

        LOG.info("╔══════════════════════════════════════════════════╗");
        LOG.info("║        BENCHMARK RESULT: {}", padRight(operation, 26));
        LOG.info("╠══════════════════════════════════════════════════╣");
        LOG.info("║  Operations:     {}", padRight(String.format("%,d", count), 26));
        LOG.info("║  Elapsed:        {} sec", padRight(String.format("%.3f", elapsedSec), 23));
        LOG.info("║  Throughput:     {} ops/sec", padRight(String.format("%.1f", throughput), 19));
        LOG.info("║  Avg Latency:    {} us", padRight(String.format("%.1f", avgLatencyUs), 22));
        LOG.info("╚══════════════════════════════════════════════════╝");
    }

    private static String padRight(String s, int len) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    // --- Fake gRPC services ---

    private static final class FakeMaster extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {
        volatile RegionRouteTable table;

        void setTable(RegionRouteTable table) {
            this.table = table;
        }

        @Override
        public void getRouteTable(GetRouteTableRequest request,
                                  StreamObserver<GetRouteTableResponse> observer) {
            observer.onNext(GetRouteTableResponse.newBuilder()
                    .setSuccess(true)
                    .setRouteTable(table)
                    .build());
            observer.onCompleted();
        }
    }

    private static final class FakeRegionServer extends RegionServerServiceGrpc.RegionServerServiceImplBase {
        final Map<ByteString, Map<String, ByteString>> store = new ConcurrentHashMap<>();

        @Override
        public void put(PutRequest request, StreamObserver<PutResponse> observer) {
            store.put(request.getKey(), request.getColumnsMap());
            observer.onNext(PutResponse.newBuilder()
                    .setSuccess(true)
                    .setSequenceId(System.currentTimeMillis())
                    .build());
            observer.onCompleted();
        }

        @Override
        public void get(GetRequest request, StreamObserver<GetResponse> observer) {
            Map<String, ByteString> columns = store.get(request.getKey());
            if (columns != null) {
                observer.onNext(GetResponse.newBuilder()
                        .setFound(true)
                        .putAllColumns(columns)
                        .setTimestamp(System.currentTimeMillis())
                        .build());
            } else {
                observer.onNext(GetResponse.newBuilder()
                        .setFound(false)
                        .build());
            }
            observer.onCompleted();
        }

        @Override
        public void delete(DeleteRequest request, StreamObserver<DeleteResponse> observer) {
            boolean existed = store.remove(request.getKey()) != null;
            observer.onNext(DeleteResponse.newBuilder()
                    .setSuccess(true)
                    .setExisted(existed)
                    .setSequenceId(System.currentTimeMillis())
                    .build());
            observer.onCompleted();
        }

        @Override
        public void exists(ExistsRequest request, StreamObserver<ExistsResponse> observer) {
            boolean found = store.containsKey(request.getKey());
            observer.onNext(ExistsResponse.newBuilder()
                    .setExists(found)
                    .build());
            observer.onCompleted();
        }
    }

    private static RegionRouteTable singleRegionTable() {
        return RegionRouteTable.newBuilder()
                .setTableName("t")
                .setVersion(1)
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId("r1")
                        .setStartKey(ByteString.EMPTY)
                        .setEndKey(ByteString.EMPTY)
                        .setPrimaryAddress("rs-1:8001")
                        .build())
                .build();
    }
}
