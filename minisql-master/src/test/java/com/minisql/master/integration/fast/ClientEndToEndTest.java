package com.minisql.master.integration.fast;

import com.google.protobuf.ByteString;
import com.minisql.client.MiniSQLClient;
import com.minisql.common.proto.RegionInfo;
import com.minisql.common.proto.ServerMetrics;
import com.minisql.common.proto.TableSchema;
import com.minisql.master.MasterServer;
import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.integration.fixtures.EmbeddedZookeeperServer;
import com.minisql.master.proto.HeartbeatRequest;
import com.minisql.master.proto.MasterServiceGrpc;
import com.minisql.master.proto.RegisterRegionServerRequest;
import com.minisql.regionserver.service.RegionServerServiceImpl;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

/**
 * End-to-end test using MiniSQLClient through the full stack.
 */
public class ClientEndToEndTest {

    private EmbeddedZookeeperServer zkServer;
    private MasterServer masterServer;
    private Server rsGrpcServer;
    private RegionServerServiceImpl rsService;
    private ManagedChannel masterChannel;
    private MiniSQLClient client;
    private MasterServiceGrpc.MasterServiceBlockingStub masterRegistrationStub;

    private int masterPort = 19000;
    private int rsPort = 19001;
    private String tableName = "e2e_users";
    private String regionId = "region-e2e-001";
    private String rsId = "e2e-rs-client";

    @Before
    public void setUp() throws Exception {
        // 1. ZK
        zkServer = new EmbeddedZookeeperServer();
        zkServer.start();

        // 2. Master
        masterServer = new MasterServer(masterPort, "e2e-master-client", zkServer.getConnectString());
        masterServer.start();
        await().atMost(15, TimeUnit.SECONDS).until(() -> masterServer.isLeader());
        Thread.sleep(2000);

        // 3. Setup metadata: table + region
        masterServer.getMetadataManager().createTable(tableName,
                TableSchema.newBuilder()
                        .setTableName(tableName)
                        .setPrimaryKey("user_id")
                        .addColumns(com.minisql.common.proto.ColumnSchema.newBuilder()
                                .setName("user_id").setType("VARCHAR").build())
                        .addColumns(com.minisql.common.proto.ColumnSchema.newBuilder()
                                .setName("name").setType("VARCHAR").build())
                        .addColumns(com.minisql.common.proto.ColumnSchema.newBuilder()
                                .setName("age").setType("INT").build())
                        .build(),
                "user_id");
        masterServer.getMetadataManager().createRegion(regionId, tableName, "", "");
        masterServer.getMetadataManager().addRegionReplica(regionId, rsId);
        masterServer.getMetadataManager().setRegionPrimary(regionId, rsId);

        // 4. RegionServer (memory backend)
        Properties props = new Properties();
        props.setProperty("storage.backend", "memory");
        props.setProperty("wal.enabled", "false");
        rsService = new RegionServerServiceImpl(rsId, props);
        rsGrpcServer = ServerBuilder.forPort(rsPort)
                .addService(rsService)
                .build()
                .start();

        // 5. Register RS with Master (so address resolution works)
        masterChannel = ManagedChannelBuilder.forAddress("localhost", masterPort)
                .usePlaintext()
                .build();
        masterRegistrationStub = MasterServiceGrpc.newBlockingStub(masterChannel);

        masterRegistrationStub.registerRegionServer(
                RegisterRegionServerRequest.newBuilder()
                        .setServerId(rsId)
                        .setHost("localhost")
                        .setPort(rsPort)
                        .setTotalMemoryMb(1024)
                        .setCpuCores(2)
                        .setDiskCapacityMb(10240)
                        .build());
        masterRegistrationStub.sendHeartbeat(HeartbeatRequest.newBuilder()
                .setServerId(rsId)
                .setTimestamp(System.currentTimeMillis())
                .setMetrics(ServerMetrics.newBuilder()
                        .setCpuUsage(20.0).setMemoryUsage(30.0)
                        .setDiskUsedBytes(256 * 1024 * 1024L)
                        .setDiskTotalBytes(10240 * 1024 * 1024L)
                        .build())
                .build());

        ClusterManager cm = masterServer.getClusterManager();
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> cm.getServerInfo(rsId) != null && cm.getServerInfo(rsId).isOnline());

        // 6. Open region on RS
        rsService.openRegion(RegionInfo.newBuilder()
                .setTableName(tableName)
                .setRegionId(regionId)
                .setPrimaryServer(rsId)
                .build());

        // 7. Create MiniSQLClient connected to Master
        client = new MiniSQLClient(masterChannel,
                new com.minisql.client.conn.ConnectionManager());
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) client.close();
        if (masterChannel != null) masterChannel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        if (rsGrpcServer != null) rsGrpcServer.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        if (masterServer != null) masterServer.stop();
        if (zkServer != null) zkServer.stop();
    }

    @Test
    public void testClientPutAndGet() {
        ByteString key = ByteString.copyFrom("user-100", StandardCharsets.UTF_8);
        Map<String, ByteString> columns = Map.of(
                "name", ByteString.copyFrom("Bob", StandardCharsets.UTF_8),
                "age", ByteString.copyFrom("25", StandardCharsets.UTF_8)
        );

        MiniSQLClient.PutResult putResult = client.put(tableName, key, columns);
        assertTrue("PUT via client should succeed", putResult.isSuccess());

        MiniSQLClient.GetResult getResult = client.get(tableName, key, null);
        assertTrue("GET via client should find the key", getResult.isFound());
        assertEquals("Bob", getResult.getColumns().get("name").toStringUtf8());
    }

    @Test
    public void testClientScan() {
        for (int i = 1; i <= 3; i++) {
            client.put(tableName,
                    ByteString.copyFrom("scan-user-" + i, StandardCharsets.UTF_8),
                    Map.of("name", ByteString.copyFrom("User" + i, StandardCharsets.UTF_8)));
        }

        List<MiniSQLClient.ScanRow> rows = client.scan(
                tableName,
                ByteString.copyFrom("scan-user-", StandardCharsets.UTF_8),
                ByteString.copyFrom("scan-user-a", StandardCharsets.UTF_8),
                10, null);
        assertEquals("Should scan 3 rows", 3, rows.size());
    }

    @Test
    public void testClientDelete() {
        ByteString key = ByteString.copyFrom("del-user", StandardCharsets.UTF_8);
        client.put(tableName, key,
                Map.of("name", ByteString.copyFrom("ToDelete", StandardCharsets.UTF_8)));

        assertTrue("GET should find before delete",
                client.get(tableName, key, null).isFound());

        MiniSQLClient.DeleteResult delResult = client.delete(tableName, key);
        assertTrue("DELETE should succeed", delResult.isSuccess());

        assertFalse("GET should not find after delete",
                client.get(tableName, key, null).isFound());
    }

    @Test
    public void testClientGetWithColumns() {
        ByteString key = ByteString.copyFrom("user-columns", StandardCharsets.UTF_8);
        Map<String, ByteString> allColumns = Map.of(
                "user_id", ByteString.copyFrom("42", StandardCharsets.UTF_8),
                "name", ByteString.copyFrom("Charlie", StandardCharsets.UTF_8),
                "age", ByteString.copyFrom("35", StandardCharsets.UTF_8)
        );

        client.put(tableName, key, allColumns);

        MiniSQLClient.GetResult result = client.get(tableName, key, List.of("name", "age"));
        assertTrue(result.isFound());
        assertEquals(2, result.getColumns().size());
        assertEquals("Charlie", result.getColumns().get("name").toStringUtf8());
        assertEquals("35", result.getColumns().get("age").toStringUtf8());
    }
}
