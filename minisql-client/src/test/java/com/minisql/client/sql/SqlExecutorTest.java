package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.client.MiniSQLClient;
import com.minisql.client.conn.ConnectionManager;
import com.minisql.client.schema.TableSchemaCache;
import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.ErrorCode;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SqlExecutorTest {

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

        master.schema = usersSchema();
        master.routeTable = twoRegionRoute();

        executor = new SqlExecutor(client, schemas);
    }

    @Test
    public void insertEncodesPrimaryKeyAndRoutes() {
        SqlResult result = executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (15, 'alice', 'a@x.com')");

        assertEquals(1, result.getUpdateCount());
        assertEquals(0, rs1.puts.size());
        assertEquals(1, rs2.puts.size());
        PutRequest req = rs2.puts.get(0);
        assertEquals("r2", req.getRegionId());
        assertEquals("alice", req.getColumnsMap().get("username").toStringUtf8());
    }

    @Test
    public void selectByPrimaryKeyReturnsSingleRow() {
        executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (5, 'bob', 'b@x.com')");

        SqlResult result = executor.execute("SELECT username, email FROM users WHERE user_id = 5");

        assertEquals(1, result.getRows().size());
        Map<String, Object> row = result.getRows().get(0);
        assertEquals("bob", row.get("username"));
        assertEquals("b@x.com", row.get("email"));
    }

    @Test
    public void selectMissingPrimaryKeyReturnsNoRows() {
        SqlResult result = executor.execute("SELECT * FROM users WHERE user_id = 99");
        assertEquals(0, result.getRows().size());
    }

    @Test
    public void selectStarExpandsToAllColumns() {
        executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (3, 'cat', 'c@x.com')");

        SqlResult result = executor.execute("SELECT * FROM users WHERE user_id = 3");

        assertEquals(List.of("user_id", "username", "email"), result.getColumns());
        Map<String, Object> row = result.getRows().get(0);
        assertEquals(3L, row.get("user_id"));
    }

    @Test
    public void selectBetweenSpansRegions() {
        executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (5, 'a', 'a@x.com')");
        executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (15, 'b', 'b@x.com')");
        executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (25, 'c', 'c@x.com')");

        SqlResult result = executor.execute(
                "SELECT user_id, username FROM users WHERE user_id BETWEEN 5 AND 20");

        assertEquals(2, result.getRows().size());
        assertTrue(result.getRows().stream()
                .anyMatch(r -> r.get("username").equals("a")));
        assertTrue(result.getRows().stream()
                .anyMatch(r -> r.get("username").equals("b")));
    }

    @Test
    public void selectGreaterThanEqualUsesOpenEnd() {
        executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (5, 'a', 'a@x.com')");
        executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (15, 'b', 'b@x.com')");
        executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (25, 'c', 'c@x.com')");

        SqlResult result = executor.execute(
                "SELECT user_id FROM users WHERE user_id >= 15");

        assertEquals(2, result.getRows().size());
    }

    @Test
    public void deleteRemovesRowAndReportsCount() {
        executor.execute(
                "INSERT INTO users (user_id, username, email) VALUES (15, 'd', 'd@x.com')");

        SqlResult result = executor.execute("DELETE FROM users WHERE user_id = 15");

        assertEquals(1, result.getUpdateCount());
        SqlResult after = executor.execute("SELECT * FROM users WHERE user_id = 15");
        assertEquals(0, after.getRows().size());
    }

    @Test
    public void deleteOfNonexistentRowReturnsZero() {
        SqlResult result = executor.execute("DELETE FROM users WHERE user_id = 999");
        assertEquals(0, result.getUpdateCount());
    }

    private static TableSchema usersSchema() {
        return TableSchema.newBuilder()
                .setTableName("users")
                .setPrimaryKey("user_id")
                .addColumns(ColumnSchema.newBuilder()
                        .setName("user_id").setType("BIGINT").setNullable(false).build())
                .addColumns(ColumnSchema.newBuilder()
                        .setName("username").setType("VARCHAR(50)").setNullable(false).build())
                .addColumns(ColumnSchema.newBuilder()
                        .setName("email").setType("VARCHAR(100)").setNullable(true).build())
                .build();
    }

    private static RegionRouteTable twoRegionRoute() {
        ByteString split = ValueCodec.encodePrimaryKey(usersSchema(), 10L);
        return RegionRouteTable.newBuilder()
                .setTableName("users")
                .setVersion(1)
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId("r1")
                        .setStartKey(ByteString.EMPTY)
                        .setEndKey(split)
                        .setPrimaryAddress("rs-1:8001")
                        .build())
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId("r2")
                        .setStartKey(split)
                        .setEndKey(ByteString.EMPTY)
                        .setPrimaryAddress("rs-2:8002")
                        .build())
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
                new TreeMap<>(SqlExecutorTest::compareUnsigned);
        final java.util.List<PutRequest> puts = new java.util.ArrayList<>();

        @Override
        public void put(PutRequest request, StreamObserver<PutResponse> observer) {
            puts.add(request);
            store.put(request.getKey(), new HashMap<>(request.getColumnsMap()));
            observer.onNext(PutResponse.newBuilder().setSuccess(true).build());
            observer.onCompleted();
        }

        @Override
        public void get(GetRequest request, StreamObserver<GetResponse> observer) {
            Map<String, ByteString> row = store.get(request.getKey());
            GetResponse.Builder builder = GetResponse.newBuilder();
            if (row == null) {
                builder.setFound(false);
            } else {
                builder.setFound(true).putAllColumns(row);
            }
            observer.onNext(builder.build());
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
