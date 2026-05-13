package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.client.MiniSQLClient;
import com.minisql.client.conn.ConnectionManager;
import com.minisql.client.schema.TableSchemaCache;
import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.RegionRouteTable;
import com.minisql.common.proto.RouteEntry;
import com.minisql.common.proto.TableSchema;
import com.minisql.master.proto.ClientMasterServiceGrpc;
import com.minisql.master.proto.GetRouteTableRequest;
import com.minisql.master.proto.GetRouteTableResponse;
import com.minisql.master.proto.GetTableSchemaRequest;
import com.minisql.master.proto.GetTableSchemaResponse;
import com.minisql.regionserver.proto.DeleteRequest;
import com.minisql.regionserver.proto.DeleteResponse;
import com.minisql.regionserver.proto.GetRequest;
import com.minisql.regionserver.proto.GetResponse;
import com.minisql.regionserver.proto.PutRequest;
import com.minisql.regionserver.proto.PutResponse;
import com.minisql.regionserver.proto.RegionServerServiceGrpc;
import com.minisql.regionserver.proto.ScanRequest;
import com.minisql.regionserver.proto.ScanResponse;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NonPkWhereTest {

    @Rule
    public final GrpcCleanupRule cleanup = new GrpcCleanupRule();

    private FakeMaster master;
    private FakeRegionServer rs1;
    private FakeRegionServer rs2;
    private SqlExecutor executor;

    @Before
    public void setUp() throws Exception {
        master = new FakeMaster();
        rs1 = new FakeRegionServer();
        rs2 = new FakeRegionServer();

        String masterName = InProcessServerBuilder.generateName();
        cleanup.register(InProcessServerBuilder.forName(masterName).directExecutor()
                .addService(master).build().start());
        cleanup.register(InProcessServerBuilder.forName("rs-1:8001").directExecutor()
                .addService(rs1).build().start());
        cleanup.register(InProcessServerBuilder.forName("rs-2:8002").directExecutor()
                .addService(rs2).build().start());

        ManagedChannel masterChannel = cleanup.register(
                InProcessChannelBuilder.forName(masterName).directExecutor().build());
        ConnectionManager cm = new ConnectionManager(address -> cleanup.register(
                InProcessChannelBuilder.forName(address).directExecutor().build()));

        MiniSQLClient client = new MiniSQLClient(masterChannel, cm);
        TableSchemaCache schemas = new TableSchemaCache(
                ClientMasterServiceGrpc.newBlockingStub(masterChannel));

        master.schema = usersSchema();
        master.routeTable = twoRegionRoute();

        executor = new SqlExecutor(client, schemas);
        seed();
    }

    private void seed() {
        insert(1L, "alice", 20L);
        insert(5L, "bob", 30L);
        insert(15L, "carol", 25L);
        insert(25L, "dave", 30L);
        insert(30L, "eve", 40L);
    }

    @Test
    public void selectByNonPrimaryKeyColumn() {
        SqlResult result = executor.execute(
                "SELECT user_id, username FROM users WHERE age = 30");

        assertEquals(2, result.getRows().size());
        assertTrue(result.getRows().stream()
                .anyMatch(r -> "bob".equals(r.get("username"))));
        assertTrue(result.getRows().stream()
                .anyMatch(r -> "dave".equals(r.get("username"))));
    }

    @Test
    public void andCombinationOfPkAndNonPkNarrowsScan() {
        SqlResult result = executor.execute(
                "SELECT user_id, username FROM users WHERE user_id >= 10 AND age = 30");

        assertEquals(1, result.getRows().size());
        assertEquals("dave", result.getRows().get(0).get("username"));

        // range [10, +inf) covers only rs2 (regions split at 10)
        assertEquals(0, rs1.scanInvocations.get());
        assertTrue(rs2.scanInvocations.get() >= 1);
    }

    @Test
    public void orBetweenPkAndNonPkScansEntireTable() {
        SqlResult result = executor.execute(
                "SELECT user_id FROM users WHERE user_id = 1 OR age = 40");

        assertEquals(2, result.getRows().size());
        // OR disables PK narrowing, so both regions are scanned
        assertTrue(rs1.scanInvocations.get() >= 1);
        assertTrue(rs2.scanInvocations.get() >= 1);
    }

    @Test
    public void notEqualFilter() {
        SqlResult result = executor.execute(
                "SELECT username FROM users WHERE age != 30");

        assertEquals(3, result.getRows().size());
    }

    @Test
    public void deleteByNonPrimaryKey() {
        SqlResult result = executor.execute("DELETE FROM users WHERE age = 30");

        assertEquals(2, result.getUpdateCount());
        SqlResult remaining = executor.execute("SELECT user_id FROM users WHERE age = 30");
        assertEquals(0, remaining.getRows().size());
    }

    @Test
    public void pointSelectOnPkStillUsesGetNotScan() {
        executor.execute("SELECT username FROM users WHERE user_id = 5");
        assertEquals(1, rs1.getInvocations.get());
        assertEquals(0, rs1.scanInvocations.get());
        assertEquals(0, rs2.scanInvocations.get());
    }

    private void insert(long id, String name, long age) {
        executor.execute(String.format(
                "INSERT INTO users (user_id, username, age) VALUES (%d, '%s', %d)",
                id, name, age));
    }

    private static TableSchema usersSchema() {
        return TableSchema.newBuilder()
                .setTableName("users").setPrimaryKey("user_id")
                .addColumns(ColumnSchema.newBuilder()
                        .setName("user_id").setType("BIGINT").setNullable(false).build())
                .addColumns(ColumnSchema.newBuilder()
                        .setName("username").setType("VARCHAR(50)").setNullable(false).build())
                .addColumns(ColumnSchema.newBuilder()
                        .setName("age").setType("BIGINT").setNullable(false).build())
                .build();
    }

    private static RegionRouteTable twoRegionRoute() {
        ByteString split = ValueCodec.encodePrimaryKey(usersSchema(), 10L);
        return RegionRouteTable.newBuilder()
                .setTableName("users").setVersion(1)
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId("r1")
                        .setStartKey(ByteString.EMPTY).setEndKey(split)
                        .setPrimaryAddress("rs-1:8001").build())
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId("r2")
                        .setStartKey(split).setEndKey(ByteString.EMPTY)
                        .setPrimaryAddress("rs-2:8002").build())
                .build();
    }

    private static final class FakeMaster extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {
        volatile TableSchema schema;
        volatile RegionRouteTable routeTable;

        @Override
        public void getRouteTable(GetRouteTableRequest request,
                                  StreamObserver<GetRouteTableResponse> observer) {
            observer.onNext(GetRouteTableResponse.newBuilder()
                    .setSuccess(true).setRouteTable(routeTable).build());
            observer.onCompleted();
        }

        @Override
        public void getTableSchema(GetTableSchemaRequest request,
                                   StreamObserver<GetTableSchemaResponse> observer) {
            observer.onNext(GetTableSchemaResponse.newBuilder()
                    .setSuccess(true).setSchema(schema).build());
            observer.onCompleted();
        }
    }

    private static final class FakeRegionServer extends RegionServerServiceGrpc.RegionServerServiceImplBase {
        final TreeMap<ByteString, Map<String, ByteString>> store =
                new TreeMap<>(NonPkWhereTest::compareUnsigned);
        final AtomicInteger scanInvocations = new AtomicInteger();
        final AtomicInteger getInvocations = new AtomicInteger();

        @Override
        public void put(PutRequest request, StreamObserver<PutResponse> observer) {
            store.put(request.getKey(), new HashMap<>(request.getColumnsMap()));
            observer.onNext(PutResponse.newBuilder().setSuccess(true).build());
            observer.onCompleted();
        }

        @Override
        public void get(GetRequest request, StreamObserver<GetResponse> observer) {
            getInvocations.incrementAndGet();
            Map<String, ByteString> row = store.get(request.getKey());
            GetResponse.Builder b = GetResponse.newBuilder();
            if (row == null) b.setFound(false);
            else b.setFound(true).putAllColumns(row);
            observer.onNext(b.build());
            observer.onCompleted();
        }

        @Override
        public void delete(DeleteRequest request, StreamObserver<DeleteResponse> observer) {
            boolean existed = store.remove(request.getKey()) != null;
            observer.onNext(DeleteResponse.newBuilder()
                    .setSuccess(true).setExisted(existed).build());
            observer.onCompleted();
        }

        @Override
        public void scan(ScanRequest request, StreamObserver<ScanResponse> observer) {
            scanInvocations.incrementAndGet();
            ByteString start = request.getStartKey();
            ByteString end = request.getEndKey();
            for (Map.Entry<ByteString, Map<String, ByteString>> e : store.entrySet()) {
                ByteString k = e.getKey();
                if (!start.isEmpty() && compareUnsigned(k, start) < 0) continue;
                if (!end.isEmpty() && compareUnsigned(k, end) >= 0) continue;
                observer.onNext(ScanResponse.newBuilder()
                        .setKey(k).putAllColumns(e.getValue()).build());
            }
            observer.onCompleted();
        }
    }

    static int compareUnsigned(ByteString a, ByteString b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int av = a.byteAt(i) & 0xFF;
            int bv = b.byteAt(i) & 0xFF;
            if (av != bv) return Integer.compare(av, bv);
        }
        return Integer.compare(a.size(), b.size());
    }

    @Test
    public void pkRangeExtractorPicksIntersectionFromAndChain() {
        TableSchema schema = usersSchema();
        Predicate pred = Predicate.and(
                Predicate.and(
                        Predicate.comparison("user_id", Predicate.Op.GTE, 5L),
                        Predicate.comparison("user_id", Predicate.Op.LTE, 20L)),
                Predicate.comparison("age", Predicate.Op.EQ, 30L));

        PkRangeExtractor.Range range = PkRangeExtractor.extract(pred, schema);

        ByteString five = ValueCodec.encodePrimaryKey(schema, 5L);
        assertEquals(five, range.start);
        // upper bound is "key of 20 + zero byte" (exclusive)
        assertTrue(!range.end.isEmpty());
    }

    @Test
    public void pkRangeExtractorReturnsUnboundedForOr() {
        TableSchema schema = usersSchema();
        Predicate pred = Predicate.or(
                Predicate.comparison("user_id", Predicate.Op.EQ, 1L),
                Predicate.comparison("user_id", Predicate.Op.EQ, 2L));

        PkRangeExtractor.Range range = PkRangeExtractor.extract(pred, schema);

        assertEquals(ByteString.EMPTY, range.start);
        assertEquals(ByteString.EMPTY, range.end);
    }
}
