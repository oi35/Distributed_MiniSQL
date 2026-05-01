package com.minisql.regionserver.service;

import com.google.protobuf.ByteString;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RegionInfo;
import com.minisql.common.proto.RegionState;
import com.minisql.regionserver.proto.AggregateQuery;
import com.minisql.regionserver.proto.ApplyReplicationLogRequest;
import com.minisql.regionserver.proto.ApplyReplicationLogResponse;
import com.minisql.regionserver.proto.GetRequest;
import com.minisql.regionserver.proto.GetResponse;
import com.minisql.regionserver.proto.MigrateRegionRequest;
import com.minisql.regionserver.proto.MigrateRegionResponse;
import com.minisql.regionserver.proto.OpenRegionRequest;
import com.minisql.regionserver.proto.OpenRegionResponse;
import com.minisql.regionserver.proto.PutRequest;
import com.minisql.regionserver.proto.PutResponse;
import com.minisql.regionserver.proto.QueryRequest;
import com.minisql.regionserver.proto.QueryResponse;
import com.minisql.regionserver.proto.ReplicationLogEntry;
import com.minisql.regionserver.proto.ScanRequest;
import com.minisql.regionserver.proto.ScanResponse;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RegionServerServiceImplTest {

    private Object service;

    @Before
    public void setUp() throws Exception {
        Properties props = new Properties();
        props.setProperty("wal.enabled", "false");
        props.setProperty("storage.backend", "memory");
        service = newService("rs-test", props);
    }

    @Test
    public void testServiceInitialization() {
        assertNotNull(service);
    }

    @Test
    public void testCliCrudRoundTrip() {
        Map<String, byte[]> columns = new HashMap<>();
        columns.put("name", "alice".getBytes(StandardCharsets.UTF_8));
        columns.put("age", "18".getBytes(StandardCharsets.UTF_8));

        assertTrue((Boolean) invoke("put",
                new Class<?>[]{String.class, String.class, String.class, Map.class},
                "users", "region-001", "user-1", columns));
        assertTrue((Boolean) invoke("exists",
                new Class<?>[]{String.class, String.class, String.class},
                "users", "region-001", "user-1"));

        @SuppressWarnings("unchecked")
        Map<String, byte[]> result = (Map<String, byte[]>) invoke("get",
                new Class<?>[]{String.class, String.class, String.class},
                "users", "region-001", "user-1");
        assertEquals("alice", new String(result.get("name"), StandardCharsets.UTF_8));
        assertEquals("18", new String(result.get("age"), StandardCharsets.UTF_8));

        assertTrue((Boolean) invoke("delete",
                new Class<?>[]{String.class, String.class, String.class},
                "users", "region-001", "user-1"));
        assertFalse((Boolean) invoke("exists",
                new Class<?>[]{String.class, String.class, String.class},
                "users", "region-001", "user-1"));
    }

    @Test
    public void testOpenRegionThenPutAndGet() {
        CapturingObserver<OpenRegionResponse> openObserver = new CapturingObserver<>();
        invoke("openRegion",
                new Class<?>[]{OpenRegionRequest.class, StreamObserver.class},
                OpenRegionRequest.newBuilder()
                .setRegion(RegionInfo.newBuilder()
                        .setRegionId("region-orders")
                        .setTableName("orders")
                        .setState(RegionState.REGION_OFFLINE)
                        .build())
                .build(), openObserver);

        assertTrue(openObserver.value.getSuccess());

        CapturingObserver<PutResponse> putObserver = new CapturingObserver<>();
        invoke("put",
                new Class<?>[]{PutRequest.class, StreamObserver.class},
                PutRequest.newBuilder()
                .setTableName("orders")
                .setRegionId("region-orders")
                .setKey(ByteString.copyFromUtf8("k1"))
                .putColumns("amount", ByteString.copyFromUtf8("20"))
                .putColumns("status", ByteString.copyFromUtf8("PAID"))
                .build(), putObserver);

        assertTrue(putObserver.value.getSuccess());
        assertTrue(putObserver.value.getSequenceId() > 0);

        CapturingObserver<GetResponse> getObserver = new CapturingObserver<>();
        invoke("get",
                new Class<?>[]{GetRequest.class, StreamObserver.class},
                GetRequest.newBuilder()
                .setTableName("orders")
                .setRegionId("region-orders")
                .setKey(ByteString.copyFromUtf8("k1"))
                .build(), getObserver);

        assertTrue(getObserver.value.getFound());
        assertEquals("20", getObserver.value.getColumnsMap().get("amount").toStringUtf8());
        assertEquals(ErrorCode.ERROR_OK, getObserver.value.getErrorCode());
    }

    @Test
    public void testScanAndAggregateQuery() {
        openRegion("region-metrics", "metrics");
        putRow("metrics", "region-metrics", "a", "score", "10");
        putRow("metrics", "region-metrics", "b", "score", "20");
        putRow("metrics", "region-metrics", "c", "score", "30");

        ScanCollector scanCollector = new ScanCollector();
        invoke("scan",
                new Class<?>[]{ScanRequest.class, StreamObserver.class},
                ScanRequest.newBuilder()
                .setTableName("metrics")
                .setRegionId("region-metrics")
                .setStartKey(ByteString.copyFromUtf8("a"))
                .setEndKey(ByteString.copyFromUtf8("z"))
                .setLimit(2)
                .build(), scanCollector);

        assertEquals(2, scanCollector.values.size());
        assertTrue(scanCollector.completed);
        assertEquals("a", scanCollector.values.get(0).getKey().toStringUtf8());
        assertTrue(scanCollector.values.get(0).getHasMore());

        CapturingObserver<QueryResponse> queryObserver = new CapturingObserver<>();
        invoke("query",
                new Class<?>[]{QueryRequest.class, StreamObserver.class},
                QueryRequest.newBuilder()
                .setTableName("metrics")
                .setRegionId("region-metrics")
                .setAggregate(AggregateQuery.newBuilder()
                        .setFunction(AggregateQuery.AggregateFunction.SUM)
                        .setColumnName("score")
                        .build())
                .build(), queryObserver);

        assertTrue(queryObserver.value.getSuccess());
        assertEquals(60D, queryObserver.value.getAggregateResult().getValue(), 0.001D);
        assertEquals(3, queryObserver.value.getAggregateResult().getCount());
    }

    @Test
    public void testApplyReplicationLogWritesReplicaData() {
        openRegion("region-replica", "users");

        ReplicationLogEntry entry = ReplicationLogEntry.newBuilder()
                .setSequenceId(99L)
                .setRegionId("region-replica")
                .setTimestamp(System.currentTimeMillis())
                .setOperation(ReplicationLogEntry.OperationType.PUT)
                .setKey(ByteString.copyFromUtf8("user-9"))
                .putColumns("name", ByteString.copyFromUtf8("replica"))
                .build();

        CapturingObserver<ApplyReplicationLogResponse> applyObserver = new CapturingObserver<>();
        invoke("applyReplicationLog",
                new Class<?>[]{ApplyReplicationLogRequest.class, StreamObserver.class},
                ApplyReplicationLogRequest.newBuilder()
                .setRegionId("region-replica")
                .addLogs(entry)
                .build(), applyObserver);

        assertTrue(applyObserver.value.getSuccess());
        assertEquals(1, applyObserver.value.getAppliedCount());
        assertEquals(99L, applyObserver.value.getLastAppliedSequence());

        CapturingObserver<GetResponse> getObserver = new CapturingObserver<>();
        invoke("get",
                new Class<?>[]{GetRequest.class, StreamObserver.class},
                GetRequest.newBuilder()
                .setTableName("users")
                .setRegionId("region-replica")
                .setKey(ByteString.copyFromUtf8("user-9"))
                .build(), getObserver);
        assertTrue(getObserver.value.getFound());
        assertEquals("replica", getObserver.value.getColumnsMap().get("name").toStringUtf8());
    }

    @Test
    public void testMigrateRegionMarksRegionAccepted() {
        openRegion("region-move", "users");

        CapturingObserver<MigrateRegionResponse> observer = new CapturingObserver<>();
        invoke("migrateRegion",
                new Class<?>[]{MigrateRegionRequest.class, StreamObserver.class},
                MigrateRegionRequest.newBuilder()
                .setRegionId("region-move")
                .setTargetServer("rs-next")
                .setMigrationId("mig-1")
                .build(), observer);

        assertTrue(observer.value.getAccepted());
        assertEquals(ErrorCode.ERROR_OK, observer.value.getErrorCode());
    }

    @Test
    public void testWalReplayRestoresRows() throws Exception {
        File walDir = new File("target/test-wal");
        deleteRecursively(walDir);
        assertTrue(walDir.mkdirs() || walDir.exists());

        Properties props = new Properties();
        props.setProperty("wal.enabled", "true");
        props.setProperty("wal.path", walDir.getAbsolutePath());
        props.setProperty("storage.backend", "memory");

        Object writerService = newService("rs-wal", props);
        invoke(writerService, "openRegion",
                new Class<?>[]{OpenRegionRequest.class, StreamObserver.class},
                OpenRegionRequest.newBuilder()
                        .setRegion(RegionInfo.newBuilder()
                                .setRegionId("region-wal")
                                .setTableName("users")
                                .build())
                        .build(),
                new CapturingObserver<>());
        invoke(writerService, "put",
                new Class<?>[]{PutRequest.class, StreamObserver.class},
                PutRequest.newBuilder()
                        .setTableName("users")
                        .setRegionId("region-wal")
                        .setKey(ByteString.copyFromUtf8("u-1"))
                        .putColumns("name", ByteString.copyFromUtf8("from-wal"))
                        .build(),
                new CapturingObserver<>());

        Object readerService = newService("rs-wal", props);
        CapturingObserver<GetResponse> getObserver = new CapturingObserver<>();
        invoke(readerService, "get",
                new Class<?>[]{GetRequest.class, StreamObserver.class},
                GetRequest.newBuilder()
                        .setTableName("users")
                        .setRegionId("region-wal")
                        .setKey(ByteString.copyFromUtf8("u-1"))
                        .build(),
                getObserver);
        assertTrue(getObserver.value.getFound());
        assertEquals("from-wal", getObserver.value.getColumnsMap().get("name").toStringUtf8());
    }

    private void openRegion(String regionId, String tableName) {
        CapturingObserver<OpenRegionResponse> observer = new CapturingObserver<>();
        invoke("openRegion",
                new Class<?>[]{OpenRegionRequest.class, StreamObserver.class},
                OpenRegionRequest.newBuilder()
                .setRegion(RegionInfo.newBuilder()
                        .setRegionId(regionId)
                        .setTableName(tableName)
                        .build())
                .build(), observer);
        assertTrue(observer.value.getSuccess());
    }

    private void putRow(String tableName, String regionId, String key, String column, String value) {
        CapturingObserver<PutResponse> observer = new CapturingObserver<>();
        invoke("put",
                new Class<?>[]{PutRequest.class, StreamObserver.class},
                PutRequest.newBuilder()
                .setTableName(tableName)
                .setRegionId(regionId)
                .setKey(ByteString.copyFromUtf8(key))
                .putColumns(column, ByteString.copyFromUtf8(value))
                .build(), observer);
        assertTrue(observer.value.getSuccess());
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        return invoke(service, methodName, parameterTypes, args);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Object newService(String serverId, Properties properties) throws Exception {
        Class<?> serviceClass = Class.forName("com.minisql.regionserver.service.RegionServerServiceImpl");
        Constructor<?> constructor = serviceClass.getConstructor(String.class, Properties.class);
        return constructor.newInstance(serverId, properties);
    }

    private void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        private T value;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public void onCompleted() {
        }
    }

    private static final class ScanCollector implements StreamObserver<ScanResponse> {
        private final List<ScanResponse> values = new ArrayList<>();
        private boolean completed;

        @Override
        public void onNext(ScanResponse value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
