package com.minisql.client;

import com.google.protobuf.ByteString;
import com.minisql.client.conn.ConnectionManager;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RegionRouteTable;
import com.minisql.common.proto.RouteEntry;
import com.minisql.master.proto.ClientMasterServiceGrpc;
import com.minisql.master.proto.GetRouteTableRequest;
import com.minisql.master.proto.GetRouteTableResponse;
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
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MiniSQLClientTest {

    @Rule
    public final GrpcCleanupRule cleanup = new GrpcCleanupRule();

    private FakeMaster master;
    private FakeRegionServer rs1;
    private FakeRegionServer rs2;
    private MiniSQLClient client;

    @Before
    public void setUp() throws Exception {
        master = new FakeMaster();
        rs1 = new FakeRegionServer();
        rs2 = new FakeRegionServer();

        String masterName = InProcessServerBuilder.generateName();
        cleanup.register(InProcessServerBuilder.forName(masterName).directExecutor()
                .addService(master).build().start());

        String rs1Name = "rs-1:8001";
        cleanup.register(InProcessServerBuilder.forName(rs1Name).directExecutor()
                .addService(rs1).build().start());

        String rs2Name = "rs-2:8002";
        cleanup.register(InProcessServerBuilder.forName(rs2Name).directExecutor()
                .addService(rs2).build().start());

        ManagedChannel masterChannel = cleanup.register(
                InProcessChannelBuilder.forName(masterName).directExecutor().build());

        ConnectionManager connectionManager = new ConnectionManager(address -> cleanup.register(
                InProcessChannelBuilder.forName(address).directExecutor().build()));

        client = new MiniSQLClient(masterChannel, connectionManager);

        master.setTable(twoRegionTable());
    }

    @After
    public void tearDown() {
        // GrpcCleanupRule handles channels and servers
    }

    @Test
    public void putRoutesToCorrectRegionServer() {
        MiniSQLClient.PutResult result = client.put("t", bytes("15"),
                Map.of("name", bytes("alice")));

        assertTrue(result.isSuccess());
        assertEquals(0, rs1.puts.size());
        assertEquals(1, rs2.puts.size());
        assertEquals("r2", rs2.puts.get(0).getRegionId());
    }

    @Test
    public void getReturnsValueFromRegionServer() {
        rs1.store.put(bytes("05"), Map.of("v", bytes("hello")));

        MiniSQLClient.GetResult result = client.get("t", bytes("05"), null);

        assertTrue(result.isFound());
        assertEquals(bytes("hello"), result.getColumns().get("v"));
    }

    @Test
    public void deleteReportsExistenceFromServer() {
        rs2.store.put(bytes("20"), Map.of("v", bytes("x")));

        MiniSQLClient.DeleteResult result = client.delete("t", bytes("20"));

        assertTrue(result.isSuccess());
        assertTrue(result.didExist());
        assertFalse(rs2.store.containsKey(bytes("20")));
    }

    @Test
    public void existsReturnsServerAnswer() {
        rs1.store.put(bytes("01"), Map.of("v", bytes("x")));

        assertTrue(client.exists("t", bytes("01")));
        assertFalse(client.exists("t", bytes("02")));
    }

    @Test
    public void staleRouteTriggersRefreshAndRetry() {
        rs1.nextPutIsStale = true;

        MiniSQLClient.PutResult result = client.put("t", bytes("05"),
                Map.of("v", bytes("x")));

        assertTrue(result.isSuccess());
        assertEquals(2, rs1.puts.size());
        assertEquals(2, master.calls.get());
    }

    @Test
    public void scanMergesAcrossRegions() {
        rs1.store.put(bytes("02"), Map.of("v", bytes("a")));
        rs1.store.put(bytes("05"), Map.of("v", bytes("b")));
        rs2.store.put(bytes("20"), Map.of("v", bytes("c")));
        rs2.store.put(bytes("25"), Map.of("v", bytes("d")));

        List<MiniSQLClient.ScanRow> rows = client.scan("t", ByteString.EMPTY, ByteString.EMPTY,
                0, null);

        assertEquals(4, rows.size());
    }

    @Test
    public void scanRespectsLimitAcrossRegions() {
        rs1.store.put(bytes("02"), Map.of("v", bytes("a")));
        rs1.store.put(bytes("05"), Map.of("v", bytes("b")));
        rs2.store.put(bytes("20"), Map.of("v", bytes("c")));

        List<MiniSQLClient.ScanRow> rows = client.scan("t", ByteString.EMPTY, ByteString.EMPTY,
                2, null);

        assertEquals(2, rows.size());
    }

    private static RegionRouteTable twoRegionTable() {
        return RegionRouteTable.newBuilder()
                .setTableName("t")
                .setVersion(1)
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId("r1")
                        .setStartKey(ByteString.EMPTY)
                        .setEndKey(bytes("10"))
                        .setPrimaryAddress("rs-1:8001")
                        .build())
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId("r2")
                        .setStartKey(bytes("10"))
                        .setEndKey(ByteString.EMPTY)
                        .setPrimaryAddress("rs-2:8002")
                        .build())
                .build();
    }

    private static ByteString bytes(String s) {
        return ByteString.copyFromUtf8(s);
    }

    private static final class FakeMaster extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {
        volatile RegionRouteTable table;
        final AtomicInteger calls = new AtomicInteger();

        void setTable(RegionRouteTable table) {
            this.table = table;
        }

        @Override
        public void getRouteTable(GetRouteTableRequest request,
                                  StreamObserver<GetRouteTableResponse> observer) {
            calls.incrementAndGet();
            observer.onNext(GetRouteTableResponse.newBuilder()
                    .setSuccess(true)
                    .setRouteTable(table)
                    .build());
            observer.onCompleted();
        }
    }

    private static final class FakeRegionServer extends RegionServerServiceGrpc.RegionServerServiceImplBase {
        final Map<ByteString, Map<String, ByteString>> store = new HashMap<>();
        final java.util.List<PutRequest> puts = new java.util.ArrayList<>();
        volatile boolean nextPutIsStale = false;

        @Override
        public void put(PutRequest request, StreamObserver<PutResponse> observer) {
            puts.add(request);
            if (nextPutIsStale) {
                nextPutIsStale = false;
                observer.onNext(PutResponse.newBuilder()
                        .setSuccess(false)
                        .setErrorCode(ErrorCode.ERROR_STALE_ROUTE)
                        .setErrorMessage("route is stale")
                        .build());
                observer.onCompleted();
                return;
            }
            store.put(request.getKey(), new HashMap<>(request.getColumnsMap()));
            observer.onNext(PutResponse.newBuilder()
                    .setSuccess(true)
                    .setSequenceId(puts.size())
                    .build());
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
                    .setSuccess(true)
                    .setExisted(existed)
                    .build());
            observer.onCompleted();
        }

        @Override
        public void exists(ExistsRequest request, StreamObserver<ExistsResponse> observer) {
            observer.onNext(ExistsResponse.newBuilder()
                    .setExists(store.containsKey(request.getKey()))
                    .build());
            observer.onCompleted();
        }

        @Override
        public void scan(ScanRequest request, StreamObserver<ScanResponse> observer) {
            for (Map.Entry<ByteString, Map<String, ByteString>> e : store.entrySet()) {
                observer.onNext(ScanResponse.newBuilder()
                        .setKey(e.getKey())
                        .putAllColumns(e.getValue())
                        .build());
            }
            observer.onCompleted();
        }
    }
}
