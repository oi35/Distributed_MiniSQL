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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发压力测试：测试系统在高并发和混合负载下的行为。
 */
public class ConcurrencyStressTest {

    private static final Logger LOG = LoggerFactory.getLogger(ConcurrencyStressTest.class);

    private static final int THREAD_COUNT = 10;
    private static final int OPS_PER_THREAD = 500;
    private static final long STRESS_DURATION_SEC = 10;

    @Rule
    public final GrpcCleanupRule cleanup = new GrpcCleanupRule();

    private StressRegionServer stressRs;
    private MiniSQLClient client;

    @Before
    public void setUp() throws Exception {
        stressRs = new StressRegionServer();

        String masterName = InProcessServerBuilder.generateName();
        cleanup.register(InProcessServerBuilder.forName(masterName).directExecutor()
                .addService(new StressFakeMaster()).build().start());

        String rsName = "rs-1:8001";
        cleanup.register(InProcessServerBuilder.forName(rsName).directExecutor()
                .addService(stressRs).build().start());

        ManagedChannel masterChannel = cleanup.register(
                InProcessChannelBuilder.forName(masterName).directExecutor().build());

        ConnectionManager connectionManager = new ConnectionManager(
                address -> cleanup.register(
                        InProcessChannelBuilder.forName(address).directExecutor().build()));

        client = new MiniSQLClient(masterChannel, connectionManager);
    }

    @After
    public void tearDown() {
        // GrpcCleanupRule handles cleanup
    }

    @Test
    public void concurrentPut() throws InterruptedException {
        LOG.info("=== STRESS TEST: Concurrent Put ===");
        LOG.info("Threads: {}, Ops/thread: {}, Total: {}",
                THREAD_COUNT, OPS_PER_THREAD, THREAD_COUNT * OPS_PER_THREAD);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger totalOps = new AtomicInteger();
        long start = System.nanoTime();

        for (int t = 0; t < THREAD_COUNT; t++) {
            int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) {
                    String key = String.format("stress-key-%d-%06d", threadId, i);
                    client.put("t", ByteString.copyFromUtf8(key),
                            Map.of("data", ByteString.copyFromUtf8("value-" + threadId + "-" + i)));
                    totalOps.incrementAndGet();
                }
                return null;
            });
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        long elapsed = System.nanoTime() - start;
        double sec = elapsed / 1_000_000_000.0;

        LOG.info("Completed: {} ops in {}", totalOps.get(), String.format("%.2fs", sec));
        LOG.info("Throughput: {} ops/sec", String.format("%.1f", totalOps.get() / sec));
        LOG.info("Total keys in store: {}", stressRs.store.size());
    }

    @Test
    public void concurrentMixed() throws InterruptedException {
        LOG.info("=== STRESS TEST: Concurrent Mixed Read/Write ===");
        LOG.info("Duration: {} sec, Threads: {}", STRESS_DURATION_SEC, THREAD_COUNT);

        for (int i = 0; i < 1000; i++) {
            stressRs.store.put(ByteString.copyFromUtf8(String.format("mixed-key-%06d", i)),
                    Map.of("data", ByteString.copyFromUtf8("initial")));
        }

        AtomicInteger writeOps = new AtomicInteger();
        AtomicInteger readOps = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        for (int t = 0; t < THREAD_COUNT; t++) {
            int threadId = t;
            executor.submit(() -> {
                Random rnd = new Random(threadId);
                long deadline = System.nanoTime() + STRESS_DURATION_SEC * 1_000_000_000L;
                try {
                    while (System.nanoTime() < deadline) {
                        String key = String.format("mixed-key-%06d", rnd.nextInt(1000));
                        if (rnd.nextBoolean()) {
                            client.put("t", ByteString.copyFromUtf8(key),
                                    Map.of("data", ByteString.copyFromUtf8(
                                            "writer-" + threadId + "-" + System.nanoTime())));
                            writeOps.incrementAndGet();
                        } else {
                            client.get("t", ByteString.copyFromUtf8(key), null);
                            readOps.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        LOG.info("Writes: {}, Reads: {}, Errors: {}", writeOps.get(), readOps.get(), errors.get());
        LOG.info("Final store size: {}", stressRs.store.size());
    }

    @Test
    public void concurrentDeleteAndGet() throws InterruptedException {
        LOG.info("=== STRESS TEST: Concurrent Delete/Get Race ===");

        int preloadCount = 2000;
        for (int i = 0; i < preloadCount; i++) {
            stressRs.store.put(
                    ByteString.copyFromUtf8(String.format("race-key-%06d", i)),
                    Map.of("data", ByteString.copyFromUtf8("val-" + i)));
        }

        AtomicInteger getsReturnedData = new AtomicInteger();
        AtomicInteger totalGets = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    Random rnd = new Random(threadId);
                    for (int i = 0; i < 500; i++) {
                        String key = String.format("race-key-%06d", rnd.nextInt(preloadCount));
                        if (rnd.nextBoolean()) {
                            MiniSQLClient.GetResult result = client.get("t",
                                    ByteString.copyFromUtf8(key), null);
                            totalGets.incrementAndGet();
                            if (result.isFound()) {
                                getsReturnedData.incrementAndGet();
                            }
                        } else {
                            client.delete("t", ByteString.copyFromUtf8(key));
                            deletes.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // expected under race
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        LOG.info("Gets: {} (found: {}), Deletes: {}",
                totalGets.get(), getsReturnedData.get(), deletes.get());
    }

    // --- Fake gRPC services ---

    private static class StressFakeMaster extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {
        private final RegionRouteTable table = RegionRouteTable.newBuilder()
                .setTableName("t")
                .setVersion(1)
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId("r1")
                        .setStartKey(ByteString.EMPTY)
                        .setEndKey(ByteString.EMPTY)
                        .setPrimaryAddress("rs-1:8001")
                        .build())
                .build();

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

    private static class StressRegionServer extends RegionServerServiceGrpc.RegionServerServiceImplBase {
        final ConcurrentHashMap<ByteString, Map<String, ByteString>> store = new ConcurrentHashMap<>();

        @Override
        public synchronized void put(PutRequest request, StreamObserver<PutResponse> observer) {
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
                observer.onNext(GetResponse.newBuilder().setFound(false).build());
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
            observer.onNext(ExistsResponse.newBuilder().setExists(found).build());
            observer.onCompleted();
        }
    }
}
