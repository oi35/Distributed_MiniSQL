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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JoinExecutorTest {

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
        ConnectionManager connectionManager = new ConnectionManager(address -> cleanup.register(
                InProcessChannelBuilder.forName(address).directExecutor().build()));

        MiniSQLClient client = new MiniSQLClient(masterChannel, connectionManager);
        TableSchemaCache schemas = new TableSchemaCache(
                ClientMasterServiceGrpc.newBlockingStub(masterChannel));

        master.tableSchemas.put("users", usersSchema());
        master.tableSchemas.put("orders", ordersSchema());
        master.routes.put("users", splitRoute("users", usersSchema()));
        master.routes.put("orders", splitRoute("orders", ordersSchema()));

        executor = new SqlExecutor(client, schemas);
    }

    @Test
    public void innerJoinReturnsOnlyMatchingRows() {
        insertUser(1L, "alice");
        insertUser(2L, "bob");
        insertUser(15L, "cat");
        insertOrder(1001L, 1L, 100L);
        insertOrder(1002L, 15L, 250L);
        insertOrder(1003L, 99L, 500L); // unmatched

        SqlResult result = executor.execute(
                "SELECT users.username, orders.amount "
                        + "FROM users JOIN orders ON users.user_id = orders.user_id");

        assertEquals(2, result.getRows().size());
        assertEquals(List.of("users.username", "orders.amount"), result.getColumns());
        assertTrue(result.getRows().stream()
                .anyMatch(r -> "alice".equals(r.get("users.username"))
                        && 100L == (long) r.get("orders.amount")));
        assertTrue(result.getRows().stream()
                .anyMatch(r -> "cat".equals(r.get("users.username"))
                        && 250L == (long) r.get("orders.amount")));
    }

    @Test
    public void joinWorksWithTableAliases() {
        insertUser(5L, "bob");
        insertOrder(2001L, 5L, 77L);

        SqlResult result = executor.execute(
                "SELECT u.username, o.amount "
                        + "FROM users u JOIN orders o ON u.user_id = o.user_id");

        assertEquals(1, result.getRows().size());
        Map<String, Object> row = result.getRows().get(0);
        assertEquals("bob", row.get("u.username"));
        assertEquals(77L, row.get("o.amount"));
    }

    @Test
    public void oneToManyJoinProducesMultipleRows() {
        insertUser(1L, "alice");
        insertOrder(1001L, 1L, 100L);
        insertOrder(1002L, 1L, 200L);
        insertOrder(1003L, 1L, 300L);

        SqlResult result = executor.execute(
                "SELECT u.username, o.amount "
                        + "FROM users u JOIN orders o ON u.user_id = o.user_id");

        assertEquals(3, result.getRows().size());
        long sum = result.getRows().stream()
                .mapToLong(r -> (long) r.get("o.amount")).sum();
        assertEquals(600L, sum);
    }

    @Test
    public void joinProjectsAllColumnsWithStar() {
        insertUser(1L, "alice");
        insertOrder(1001L, 1L, 99L);

        SqlResult result = executor.execute(
                "SELECT * FROM users u JOIN orders o ON u.user_id = o.user_id");

        // left cols then right cols, qualified by alias
        assertEquals(List.of("u.user_id", "u.username",
                        "o.order_id", "o.user_id", "o.amount"),
                result.getColumns());
        Map<String, Object> row = result.getRows().get(0);
        assertEquals(1L, row.get("u.user_id"));
        assertEquals(1L, row.get("o.user_id"));
        assertEquals("alice", row.get("u.username"));
        assertEquals(1001L, row.get("o.order_id"));
    }

    @Test
    public void emptyLeftTableProducesNoRows() {
        insertOrder(1001L, 1L, 100L);

        SqlResult result = executor.execute(
                "SELECT u.username, o.amount "
                        + "FROM users u JOIN orders o ON u.user_id = o.user_id");

        assertEquals(0, result.getRows().size());
    }

    private void insertUser(long id, String name) {
        executor.execute(
                String.format("INSERT INTO users (user_id, username) VALUES (%d, '%s')",
                        id, name));
    }

    private void insertOrder(long orderId, long userId, long amount) {
        executor.execute(
                String.format("INSERT INTO orders (order_id, user_id, amount) VALUES (%d, %d, %d)",
                        orderId, userId, amount));
    }

    private static TableSchema usersSchema() {
        return TableSchema.newBuilder()
                .setTableName("users").setPrimaryKey("user_id")
                .addColumns(ColumnSchema.newBuilder()
                        .setName("user_id").setType("BIGINT").setNullable(false).build())
                .addColumns(ColumnSchema.newBuilder()
                        .setName("username").setType("VARCHAR(50)").setNullable(false).build())
                .build();
    }

    private static TableSchema ordersSchema() {
        return TableSchema.newBuilder()
                .setTableName("orders").setPrimaryKey("order_id")
                .addColumns(ColumnSchema.newBuilder()
                        .setName("order_id").setType("BIGINT").setNullable(false).build())
                .addColumns(ColumnSchema.newBuilder()
                        .setName("user_id").setType("BIGINT").setNullable(false).build())
                .addColumns(ColumnSchema.newBuilder()
                        .setName("amount").setType("BIGINT").setNullable(false).build())
                .build();
    }

    private static RegionRouteTable splitRoute(String tableName, TableSchema schema) {
        ByteString split = ValueCodec.encodePrimaryKey(schema, 10L);
        return RegionRouteTable.newBuilder()
                .setTableName(tableName)
                .setVersion(1)
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId(tableName + "-r1")
                        .setStartKey(ByteString.EMPTY)
                        .setEndKey(split)
                        .setPrimaryAddress("rs-1:8001")
                        .build())
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId(tableName + "-r2")
                        .setStartKey(split)
                        .setEndKey(ByteString.EMPTY)
                        .setPrimaryAddress("rs-2:8002")
                        .build())
                .build();
    }

    private static final class FakeMaster extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {
        final Map<String, TableSchema> tableSchemas = new HashMap<>();
        final Map<String, RegionRouteTable> routes = new HashMap<>();

        @Override
        public void getRouteTable(GetRouteTableRequest request,
                                  StreamObserver<GetRouteTableResponse> observer) {
            observer.onNext(GetRouteTableResponse.newBuilder()
                    .setSuccess(true)
                    .setRouteTable(routes.get(request.getTableName()))
                    .build());
            observer.onCompleted();
        }

        @Override
        public void getTableSchema(GetTableSchemaRequest request,
                                   StreamObserver<GetTableSchemaResponse> observer) {
            observer.onNext(GetTableSchemaResponse.newBuilder()
                    .setSuccess(true)
                    .setSchema(tableSchemas.get(request.getTableName()))
                    .build());
            observer.onCompleted();
        }
    }

    private static final class FakeRegionServer extends RegionServerServiceGrpc.RegionServerServiceImplBase {
        // Stored per (table, key) because one server hosts regions of multiple tables.
        final Map<String, TreeMap<ByteString, Map<String, ByteString>>> stores = new HashMap<>();

        @Override
        public void put(PutRequest request, StreamObserver<PutResponse> observer) {
            stores.computeIfAbsent(request.getTableName(),
                            t -> new TreeMap<>(JoinExecutorTest::compareUnsigned))
                    .put(request.getKey(), new HashMap<>(request.getColumnsMap()));
            observer.onNext(PutResponse.newBuilder().setSuccess(true).build());
            observer.onCompleted();
        }

        @Override
        public void scan(ScanRequest request, StreamObserver<ScanResponse> observer) {
            TreeMap<ByteString, Map<String, ByteString>> store =
                    stores.getOrDefault(request.getTableName(), new TreeMap<>());
            ByteString start = request.getStartKey();
            ByteString end = request.getEndKey();
            for (Map.Entry<ByteString, Map<String, ByteString>> e : store.entrySet()) {
                ByteString key = e.getKey();
                if (!start.isEmpty() && compareUnsigned(key, start) < 0) continue;
                if (!end.isEmpty() && compareUnsigned(key, end) >= 0) continue;
                observer.onNext(ScanResponse.newBuilder()
                        .setKey(key).putAllColumns(e.getValue()).build());
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
}
