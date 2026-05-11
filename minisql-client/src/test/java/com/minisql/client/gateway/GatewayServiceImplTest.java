package com.minisql.client.gateway;

import com.google.protobuf.ByteString;
import com.minisql.client.MiniSQLClient;
import com.minisql.client.conn.ConnectionManager;
import com.minisql.client.gateway.proto.ExecuteRequest;
import com.minisql.client.gateway.proto.ExecuteResponse;
import com.minisql.client.gateway.proto.GatewayServiceGrpc;
import com.minisql.client.gateway.proto.PingResponse;
import com.minisql.client.gateway.proto.Row;
import com.minisql.client.gateway.proto.TypedValue;
import com.minisql.client.schema.TableSchemaCache;
import com.minisql.client.sql.SqlExecutor;
import com.minisql.client.sql.ValueCodec;
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
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GatewayServiceImplTest {

    @Rule
    public final GrpcCleanupRule cleanup = new GrpcCleanupRule();

    private GatewayServiceGrpc.GatewayServiceBlockingStub gateway;

    @Before
    public void setUp() throws Exception {
        FakeMaster master = new FakeMaster();
        FakeRegionServer rs1 = new FakeRegionServer();
        FakeRegionServer rs2 = new FakeRegionServer();

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

        SqlExecutor executor = new SqlExecutor(client, schemas);

        String gatewayName = InProcessServerBuilder.generateName();
        cleanup.register(InProcessServerBuilder.forName(gatewayName).directExecutor()
                .addService(new GatewayServiceImpl(executor)).build().start());
        ManagedChannel gatewayChannel = cleanup.register(
                InProcessChannelBuilder.forName(gatewayName).directExecutor().build());
        gateway = GatewayServiceGrpc.newBlockingStub(gatewayChannel);
    }

    @Test
    public void pingReturnsVersion() {
        PingResponse response = gateway.ping(com.minisql.client.gateway.proto.PingRequest.newBuilder().build());
        assertFalse(response.getVersion().isEmpty());
        assertTrue(response.getServerTimeMs() > 0);
    }

    @Test
    public void insertReportsAffectedRows() {
        ExecuteResponse response = gateway.execute(ExecuteRequest.newBuilder()
                .setSql("INSERT INTO users (user_id, username) VALUES (1, 'alice')")
                .build());

        assertTrue(response.getSuccess());
        assertEquals(1, response.getUpdate().getAffectedRows());
    }

    @Test
    public void selectReturnsTypedRows() {
        gateway.execute(ExecuteRequest.newBuilder()
                .setSql("INSERT INTO users (user_id, username) VALUES (1, 'alice')")
                .build());
        gateway.execute(ExecuteRequest.newBuilder()
                .setSql("INSERT INTO users (user_id, username) VALUES (15, 'bob')")
                .build());

        ExecuteResponse response = gateway.execute(ExecuteRequest.newBuilder()
                .setSql("SELECT user_id, username FROM users WHERE user_id >= 10")
                .build());

        assertTrue(response.getSuccess());
        assertEquals(2, response.getQuery().getColumnsCount());
        assertEquals(1, response.getQuery().getRowsCount());
        Row row = response.getQuery().getRows(0);
        assertEquals(TypedValue.ValueCase.INT_VALUE, row.getValues(0).getValueCase());
        assertEquals(15L, row.getValues(0).getIntValue());
        assertEquals("bob", row.getValues(1).getStringValue());
    }

    @Test
    public void invalidSqlReportsError() {
        ExecuteResponse response = gateway.execute(ExecuteRequest.newBuilder()
                .setSql("SELECT * FROM users WHERE user_id = broken@!@")
                .build());

        assertFalse(response.getSuccess());
        assertFalse(response.getErrorCode().isEmpty());
    }

    @Test
    public void errorFromExecutorSurfacesErrorCode() {
        ExecuteResponse response = gateway.execute(ExecuteRequest.newBuilder()
                .setSql("UPDATE users SET username = 'x' WHERE user_id = 1")
                .build());

        assertFalse(response.getSuccess());
        assertEquals("ERROR_UNIMPLEMENTED", response.getErrorCode());
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
                new TreeMap<>(GatewayServiceImplTest::compareUnsigned);

        @Override
        public void put(PutRequest request, StreamObserver<PutResponse> observer) {
            store.put(request.getKey(), new HashMap<>(request.getColumnsMap()));
            observer.onNext(PutResponse.newBuilder().setSuccess(true).build());
            observer.onCompleted();
        }

        @Override
        public void get(GetRequest request, StreamObserver<GetResponse> observer) {
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
}
