package com.minisql.admin;

import com.minisql.common.proto.RouteEntry;
import com.minisql.common.proto.ServerState;
import com.minisql.master.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.*;

public class AdminGrpcClientTest {

    @Rule
    public final GrpcCleanupRule cleanup = new GrpcCleanupRule();

    private AdminGrpcClient client;
    private ByteArrayOutputStream out;
    private PrintStream originalOut;

    @Before
    public void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        cleanup.register(InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(new FakeAdminService())
                .addService(new FakeClientMasterService())
                .build().start());

        ManagedChannel channel = cleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());

        client = new AdminGrpcClient(channel);

        out = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(out));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    public void testPrintClusterHealth() {
        client.printClusterHealth();
        String output = out.toString();
        assertTrue(output.contains("Cluster Health"));
        assertTrue(output.contains("HEALTHY"));
        assertTrue(output.contains("Total Servers"));
        assertTrue(output.contains("Online Servers"));
        assertTrue(output.contains("Total Regions"));
        assertTrue(output.contains("Online Regions"));
    }

    @Test
    public void testPrintClusterStats() {
        client.printClusterStats();
        String output = out.toString();
        assertTrue(output.contains("Cluster Statistics"));
        assertTrue(output.contains("Total Tables"));
        assertTrue(output.contains("Total Regions"));
        assertTrue(output.contains("Total Data Size"));
        assertTrue(output.contains("Total Rows"));
    }

    @Test
    public void testPrintServerList() {
        client.printServerList();
        String output = out.toString();
        assertTrue(output.contains("RegionServer List"));
        assertTrue(output.contains("rs-001"));
        assertTrue(output.contains("ONLINE"));
        assertTrue(output.contains("rs-002"));
        assertTrue(output.contains("2/2 online"));
    }

    @Test
    public void testTriggerBalance() {
        client.triggerBalance();
        String output = out.toString();
        assertTrue(output.contains("Balance Result"));
        assertTrue(output.contains("SUCCESS"));
        assertTrue(output.contains("Plans Generated: 2"));
    }

    @Test
    public void testPrintTableList() {
        client.printTableList();
        String output = out.toString();
        assertTrue(output.contains("Tables"));
        assertTrue(output.contains("users"));
        assertTrue(output.contains("orders"));
    }

    @Test
    public void testPrintTableSchema() {
        client.printTableSchema("users");
        String output = out.toString();
        assertTrue(output.contains("Table: users"));
        assertTrue(output.contains("Primary Key"));
        assertTrue(output.contains("id"));
        assertTrue(output.contains("name"));
        assertTrue(output.contains("VARCHAR"));
    }

    @Test
    public void testPrintTableSchemaNotFound() {
        client.printTableSchema("nonexistent");
        String output = out.toString();
        assertTrue(output.contains("Error"));
    }

    @Test
    public void testPrintTableRoute() {
        client.printTableRoute("users");
        String output = out.toString();
        assertTrue(output.contains("Route Table: users"));
        assertTrue(output.contains("Regions"));
        assertTrue(output.contains("region-001"));
        assertTrue(output.contains("region-002"));
    }

    @Test
    public void testPrintTableRouteNotFound() {
        client.printTableRoute("unknown_table");
        String output = out.toString();
        assertTrue(output.contains("Error"));
    }

    @Test
    public void testFormatBytes() throws Exception {
        var constructor = AdminGrpcClient.class.getConstructor(String.class, int.class);
        var instance = constructor.newInstance("localhost", 0);
        var formatBytes = AdminGrpcClient.class.getDeclaredMethod("formatBytes", long.class);
        formatBytes.setAccessible(true);

        assertEquals("0 B", formatBytes.invoke(instance, 0));
        assertEquals("512 B", formatBytes.invoke(instance, 512));
        assertEquals("1.0 KB", formatBytes.invoke(instance, 1024));
        assertEquals("1.5 KB", formatBytes.invoke(instance, 1536));
        assertEquals("1.0 MB", formatBytes.invoke(instance, 1024 * 1024));
        assertEquals("1.00 GB", formatBytes.invoke(instance, 1024 * 1024 * 1024L));
    }

    @Test
    public void testFormatUptime() throws Exception {
        var constructor = AdminGrpcClient.class.getConstructor(String.class, int.class);
        var instance = constructor.newInstance("localhost", 0);
        var formatUptime = AdminGrpcClient.class.getDeclaredMethod("formatUptime", long.class);
        formatUptime.setAccessible(true);

        assertEquals("0s", formatUptime.invoke(instance, 0));
        assertEquals("30s", formatUptime.invoke(instance, 30000));
        assertEquals("1m 0s", formatUptime.invoke(instance, 60000));
        assertEquals("1h 0m", formatUptime.invoke(instance, 3600000));
        assertEquals("1d 0h", formatUptime.invoke(instance, 86400000));
        assertEquals("2d 3h", formatUptime.invoke(instance, 183600000));
    }

    // ---- Fake gRPC service implementations ----

    private static class FakeAdminService extends AdminServiceGrpc.AdminServiceImplBase {
        @Override
        public void listServers(ListServersRequest request, StreamObserver<ListServersResponse> responseObserver) {
            responseObserver.onNext(ListServersResponse.newBuilder()
                    .setTotalCount(2)
                    .setOnlineCount(2)
                    .addServers(ServerDetail.newBuilder()
                            .setServerId("rs-001")
                            .setAddress("localhost:9001")
                            .setState(ServerState.SERVER_ONLINE)
                            .setLoadScore(0.5)
                            .setRegionCount(3)
                            .setTotalSizeBytes(1024 * 1024)
                            .setUptimeMs(3600000)
                            .setLastHeartbeatTime(1715500000000L)
                            .build())
                    .addServers(ServerDetail.newBuilder()
                            .setServerId("rs-002")
                            .setAddress("localhost:9002")
                            .setState(ServerState.SERVER_OFFLINE)
                            .setLoadScore(0.0)
                            .setRegionCount(0)
                            .setTotalSizeBytes(0)
                            .setUptimeMs(0)
                            .setLastHeartbeatTime(0L)
                            .build())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void triggerBalance(TriggerBalanceRequest request, StreamObserver<TriggerBalanceResponse> responseObserver) {
            responseObserver.onNext(TriggerBalanceResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Balance completed successfully")
                    .setPlansGenerated(2)
                    .build());
            responseObserver.onCompleted();
        }
    }

    public static class FakeClientMasterService extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {
        @Override
        public void getClusterHealth(GetClusterHealthRequest request, StreamObserver<GetClusterHealthResponse> responseObserver) {
            responseObserver.onNext(GetClusterHealthResponse.newBuilder()
                    .setStatus(GetClusterHealthResponse.HealthStatus.HEALTHY)
                    .setTotalServers(2)
                    .setOnlineServers(2)
                    .setTotalRegions(5)
                    .setOnlineRegions(5)
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void getClusterStats(GetClusterStatsRequest request, StreamObserver<GetClusterStatsResponse> responseObserver) {
            responseObserver.onNext(GetClusterStatsResponse.newBuilder()
                    .setTotalTables(3)
                    .setTotalRegions(5)
                    .setTotalDataSizeBytes(1024 * 1024)
                    .setTotalRowCount(1000)
                    .setAverageRegionSizeMb(0.5)
                    .setRegionsSplitting(0)
                    .setRegionsMigrating(0)
                    .setTotalQps(50)
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void listTables(ListTablesRequest request, StreamObserver<ListTablesResponse> responseObserver) {
            responseObserver.onNext(ListTablesResponse.newBuilder()
                    .addTableNames("users")
                    .addTableNames("orders")
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void getTableSchema(GetTableSchemaRequest request, StreamObserver<GetTableSchemaResponse> responseObserver) {
            if ("nonexistent".equals(request.getTableName())) {
                responseObserver.onNext(GetTableSchemaResponse.newBuilder()
                        .setSuccess(false)
                        .setErrorMessage("Table not found: nonexistent")
                        .build());
            } else {
                responseObserver.onNext(GetTableSchemaResponse.newBuilder()
                        .setSuccess(true)
                        .setSchema(com.minisql.common.proto.TableSchema.newBuilder()
                                .setTableName(request.getTableName())
                                .setPrimaryKey("id")
                                .setVersion(1)
                                .addColumns(com.minisql.common.proto.ColumnSchema.newBuilder()
                                        .setName("id")
                                        .setType("BIGINT")
                                        .setNullable(false)
                                        .setDefaultValue("")
                                        .build())
                                .addColumns(com.minisql.common.proto.ColumnSchema.newBuilder()
                                        .setName("name")
                                        .setType("VARCHAR")
                                        .setNullable(true)
                                        .setDefaultValue("")
                                        .build())
                                .build())
                        .build());
            }
            responseObserver.onCompleted();
        }

        @Override
        public void getRouteTable(GetRouteTableRequest request, StreamObserver<GetRouteTableResponse> responseObserver) {
            if ("unknown_table".equals(request.getTableName())) {
                responseObserver.onNext(GetRouteTableResponse.newBuilder()
                        .setSuccess(false)
                        .setErrorMessage("Route not found")
                        .build());
            } else {
                responseObserver.onNext(GetRouteTableResponse.newBuilder()
                        .setSuccess(true)
                        .setRouteTable(com.minisql.common.proto.RegionRouteTable.newBuilder()
                                .setTableName(request.getTableName())
                                .setVersion(1)
                                .addRoutes(RouteEntry.newBuilder()
                                        .setRegionId("region-001")
                                        .setStartKey(com.google.protobuf.ByteString.copyFromUtf8(""))
                                        .setEndKey(com.google.protobuf.ByteString.copyFromUtf8("user_500"))
                                        .setPrimaryServer("rs-001")
                                        .build())
                                .addRoutes(RouteEntry.newBuilder()
                                        .setRegionId("region-002")
                                        .setStartKey(com.google.protobuf.ByteString.copyFromUtf8("user_500"))
                                        .setEndKey(com.google.protobuf.ByteString.copyFromUtf8(""))
                                        .setPrimaryServer("rs-002")
                                        .build())
                                .build())
                        .build());
            }
            responseObserver.onCompleted();
        }
    }
}
