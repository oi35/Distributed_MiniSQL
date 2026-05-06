package com.minisql.client.route;

import com.google.protobuf.ByteString;
import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RegionRouteTable;
import com.minisql.common.proto.RouteEntry;
import com.minisql.master.proto.ClientMasterServiceGrpc;
import com.minisql.master.proto.GetRouteTableRequest;
import com.minisql.master.proto.GetRouteTableResponse;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RouteCacheTest {

    @Rule
    public final GrpcCleanupRule cleanup = new GrpcCleanupRule();

    private FakeMaster fakeMaster;
    private RouteCache cache;

    @Before
    public void setUp() throws Exception {
        fakeMaster = new FakeMaster();
        String name = InProcessServerBuilder.generateName();
        cleanup.register(InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(fakeMaster)
                .build()
                .start());
        ManagedChannel channel = cleanup.register(
                InProcessChannelBuilder.forName(name).directExecutor().build());
        cache = new RouteCache(ClientMasterServiceGrpc.newBlockingStub(channel));
    }

    @After
    public void tearDown() {
        // cleanup rule handles channel/server shutdown
    }

    @Test
    public void lookupPicksCorrectRegionAcrossBoundaries() {
        fakeMaster.setTable(threeRegionTable());

        RouteEntry r1 = cache.lookup("t", bytes("05"));
        RouteEntry r2 = cache.lookup("t", bytes("10"));
        RouteEntry r3 = cache.lookup("t", bytes("25"));
        RouteEntry r4 = cache.lookup("t", bytes("99"));

        assertEquals("r1", r1.getRegionId());
        assertEquals("r2", r2.getRegionId());
        assertEquals("r2", r3.getRegionId());
        assertEquals("r3", r4.getRegionId());
    }

    @Test
    public void lookupCachesTableAcrossCalls() {
        fakeMaster.setTable(threeRegionTable());

        cache.lookup("t", bytes("05"));
        cache.lookup("t", bytes("25"));
        cache.lookup("t", bytes("99"));

        assertEquals(1, fakeMaster.calls.get());
    }

    @Test
    public void invalidateForcesRefetch() {
        fakeMaster.setTable(threeRegionTable());
        cache.lookup("t", bytes("05"));

        cache.invalidate("t");
        cache.lookup("t", bytes("05"));

        assertEquals(2, fakeMaster.calls.get());
    }

    @Test
    public void lookupThrowsWhenMasterFails() {
        fakeMaster.setTable(null);
        MiniSQLClientException ex = assertThrows(MiniSQLClientException.class,
                () -> cache.lookup("t", bytes("00")));
        assertEquals(ErrorCode.ERROR_TABLE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    public void lookupRangeReturnsOverlappingRegionsOnly() {
        fakeMaster.setTable(threeRegionTable());

        List<RouteEntry> hits = cache.lookupRange("t", bytes("08"), bytes("22"));

        assertEquals(2, hits.size());
        assertEquals("r1", hits.get(0).getRegionId());
        assertEquals("r2", hits.get(1).getRegionId());
    }

    @Test
    public void lookupRangeWithOpenEndCoversTail() {
        fakeMaster.setTable(threeRegionTable());

        List<RouteEntry> hits = cache.lookupRange("t", bytes("20"), ByteString.EMPTY);

        assertEquals(2, hits.size());
        assertTrue(hits.stream().anyMatch(r -> r.getRegionId().equals("r2")));
        assertTrue(hits.stream().anyMatch(r -> r.getRegionId().equals("r3")));
    }

    private static RegionRouteTable threeRegionTable() {
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
                        .setEndKey(bytes("30"))
                        .setPrimaryAddress("rs-2:8002")
                        .build())
                .addRoutes(RouteEntry.newBuilder()
                        .setRegionId("r3")
                        .setStartKey(bytes("30"))
                        .setEndKey(ByteString.EMPTY)
                        .setPrimaryAddress("rs-3:8003")
                        .build())
                .build();
    }

    private static ByteString bytes(String s) {
        return ByteString.copyFromUtf8(s);
    }

    private static final class FakeMaster extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {
        private volatile RegionRouteTable table;
        final AtomicInteger calls = new AtomicInteger();

        void setTable(RegionRouteTable table) {
            this.table = table;
        }

        @Override
        public void getRouteTable(GetRouteTableRequest request,
                                  StreamObserver<GetRouteTableResponse> observer) {
            calls.incrementAndGet();
            GetRouteTableResponse.Builder response = GetRouteTableResponse.newBuilder();
            if (table == null) {
                response.setSuccess(false)
                        .setErrorCode(ErrorCode.ERROR_TABLE_NOT_FOUND)
                        .setErrorMessage("table not found");
            } else {
                response.setSuccess(true).setRouteTable(table);
            }
            observer.onNext(response.build());
            observer.onCompleted();
        }
    }
}
