package com.minisql.master.integration.fast;

import com.google.protobuf.ByteString;
import com.minisql.common.proto.RegionInfo;
import com.minisql.common.proto.ServerMetrics;
import com.minisql.master.MasterServer;
import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.integration.fixtures.EmbeddedZookeeperServer;
import com.minisql.master.proto.HeartbeatRequest;
import com.minisql.master.proto.HeartbeatResponse;
import com.minisql.master.proto.MasterServiceGrpc;
import com.minisql.master.proto.RegisterRegionServerRequest;
import com.minisql.master.proto.RegisterRegionServerResponse;
import com.minisql.regionserver.proto.GetRequest;
import com.minisql.regionserver.proto.GetResponse;
import com.minisql.regionserver.proto.PutRequest;
import com.minisql.regionserver.proto.PutResponse;
import com.minisql.regionserver.proto.RegionServerServiceGrpc;
import com.minisql.regionserver.proto.ScanRequest;
import com.minisql.regionserver.proto.ScanResponse;
import com.minisql.regionserver.service.RegionServerServiceImpl;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

/**
 * Full-stack smoke test: ZK + Master + real RegionServer (memory backend) + gRPC data ops.
 */
public class EndToEndSmokeTest {

    private EmbeddedZookeeperServer zkServer;
    private MasterServer masterServer;
    private Server rsGrpcServer;
    private RegionServerServiceImpl rsService;
    private ManagedChannel masterChannel;
    private ManagedChannel rsChannel;
    private MasterServiceGrpc.MasterServiceBlockingStub masterStub;
    private RegionServerServiceGrpc.RegionServerServiceBlockingStub rsStub;
    private String regionId = "region-smoke-001";
    private String tableName = "smoke_test";
    private int masterPort = 18000;
    private int rsPort = 18001;

    @Before
    public void setUp() throws Exception {
        // 1. Start Embedded ZK
        zkServer = new EmbeddedZookeeperServer();
        zkServer.start();

        // 2. Start Master server
        masterServer = new MasterServer(masterPort, "e2e-master", zkServer.getConnectString());
        masterServer.start();

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> masterServer.isLeader());
        Thread.sleep(2000);

        // 3. Start real RegionServer service (memory backend, no WAL)
        Properties props = new Properties();
        props.setProperty("storage.backend", "memory");
        props.setProperty("wal.enabled", "false");
        rsService = new RegionServerServiceImpl("e2e-rs-001", props);
        rsGrpcServer = ServerBuilder.forPort(rsPort)
                .addService(rsService)
                .build()
                .start();

        // 4. Create channels and stubs
        masterChannel = ManagedChannelBuilder.forAddress("localhost", masterPort)
                .usePlaintext()
                .build();
        masterStub = MasterServiceGrpc.newBlockingStub(masterChannel);

        rsChannel = ManagedChannelBuilder.forAddress("localhost", rsPort)
                .usePlaintext()
                .build();
        rsStub = RegionServerServiceGrpc.newBlockingStub(rsChannel);

        // 5. Register RS with Master
        RegisterRegionServerResponse regResponse = masterStub.registerRegionServer(
                RegisterRegionServerRequest.newBuilder()
                        .setServerId("e2e-rs-001")
                        .setHost("localhost")
                        .setPort(rsPort)
                        .setTotalMemoryMb(1024)
                        .setCpuCores(2)
                        .setDiskCapacityMb(10240)
                        .build());
        assertTrue("RegionServer should register successfully", regResponse.getSuccess());

        // 6. Send heartbeat to update status
        masterStub.sendHeartbeat(HeartbeatRequest.newBuilder()
                .setServerId("e2e-rs-001")
                .setTimestamp(System.currentTimeMillis())
                .setMetrics(ServerMetrics.newBuilder()
                        .setCpuUsage(30.0)
                        .setMemoryUsage(40.0)
                        .setDiskUsedBytes(512L * 1024 * 1024)
                        .setDiskTotalBytes(10240L * 1024 * 1024)
                        .build())
                .build());

        // 7. Wait for Master to acknowledge the RS
        ClusterManager cm = masterServer.getClusterManager();
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> cm.getServerInfo("e2e-rs-001") != null);
        ServerInfo si = cm.getServerInfo("e2e-rs-001");
        assertNotNull("RS should be in ClusterManager", si);
        assertTrue("RS should be online", si.isOnline());

        // 8. Open region on RS
        rsService.openRegion(RegionInfo.newBuilder()
                .setTableName(tableName)
                .setRegionId(regionId)
                .setPrimaryServer("e2e-rs-001")
                .build());
    }

    @After
    public void tearDown() throws Exception {
        if (rsChannel != null) rsChannel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        if (masterChannel != null) masterChannel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        if (rsGrpcServer != null) rsGrpcServer.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        if (masterServer != null) masterServer.stop();
        if (zkServer != null) zkServer.stop();
    }

    @Test
    public void testPutAndGet() {
        ByteString key = ByteString.copyFrom("user-001", StandardCharsets.UTF_8);
        Map<String, ByteString> columns = new HashMap<>();
        columns.put("name", ByteString.copyFrom("Alice", StandardCharsets.UTF_8));
        columns.put("age", ByteString.copyFrom("30", StandardCharsets.UTF_8));

        PutResponse putResp = rsStub.put(PutRequest.newBuilder()
                .setTableName(tableName)
                .setRegionId(regionId)
                .setKey(key)
                .putAllColumns(columns)
                .setTimestamp(System.currentTimeMillis())
                .build());
        assertTrue("PUT should succeed", putResp.getSuccess());

        GetResponse getResp = rsStub.get(GetRequest.newBuilder()
                .setTableName(tableName)
                .setRegionId(regionId)
                .setKey(key)
                .build());
        assertTrue("GET should find the key", getResp.getFound());
        assertEquals("Alice", getResp.getColumnsOrDefault("name", ByteString.EMPTY).toStringUtf8());
        assertEquals("30", getResp.getColumnsOrDefault("age", ByteString.EMPTY).toStringUtf8());
    }

    @Test
    public void testScan() {
        for (int i = 1; i <= 3; i++) {
            ByteString key = ByteString.copyFrom("scan-" + i, StandardCharsets.UTF_8);
            Map<String, ByteString> cols = new HashMap<>();
            cols.put("val", ByteString.copyFrom("data-" + i, StandardCharsets.UTF_8));
            rsStub.put(PutRequest.newBuilder()
                    .setTableName(tableName)
                    .setRegionId(regionId)
                    .setKey(key)
                    .putAllColumns(cols)
                    .setTimestamp(System.currentTimeMillis())
                    .build());
        }

        Iterator<ScanResponse> iter = rsStub.scan(ScanRequest.newBuilder()
                .setTableName(tableName)
                .setRegionId(regionId)
                .setStartKey(ByteString.copyFrom("scan-", StandardCharsets.UTF_8))
                .setEndKey(ByteString.copyFrom("scan-a", StandardCharsets.UTF_8))
                .setLimit(10)
                .build());

        int count = 0;
        while (iter.hasNext()) {
            ScanResponse row = iter.next();
            assertNotNull(row.getKey());
            count++;
        }
        assertEquals("Should scan 3 rows", 3, count);
    }

    @Test
    public void testDelete() {
        ByteString key = ByteString.copyFrom("del-key", StandardCharsets.UTF_8);
        Map<String, ByteString> cols = new HashMap<>();
        cols.put("col1", ByteString.copyFrom("val1", StandardCharsets.UTF_8));

        rsStub.put(PutRequest.newBuilder()
                .setTableName(tableName)
                .setRegionId(regionId)
                .setKey(key)
                .putAllColumns(cols)
                .setTimestamp(System.currentTimeMillis())
                .build());

        assertTrue("Key should exist before delete",
                rsStub.get(GetRequest.newBuilder()
                        .setTableName(tableName).setRegionId(regionId).setKey(key).build())
                        .getFound());

        com.minisql.regionserver.proto.DeleteResponse delResp = rsStub.delete(
                com.minisql.regionserver.proto.DeleteRequest.newBuilder()
                        .setTableName(tableName)
                        .setRegionId(regionId)
                        .setKey(key)
                        .build());
        assertTrue("DELETE should succeed", delResp.getSuccess());

        assertFalse("Key should be gone after delete",
                rsStub.get(GetRequest.newBuilder()
                        .setTableName(tableName).setRegionId(regionId).setKey(key).build())
                        .getFound());
    }

    @Test
    public void testMasterRegistrationAndHeartbeat() {
        ClusterManager cm = masterServer.getClusterManager();
        ServerInfo si = cm.getServerInfo("e2e-rs-001");
        assertNotNull("RS must be registered", si);
        assertEquals("e2e-rs-001", si.getServerId());
        assertTrue("RS must be online", si.isOnline());

        HeartbeatResponse hbResp = masterStub.sendHeartbeat(HeartbeatRequest.newBuilder()
                .setServerId("e2e-rs-001")
                .setTimestamp(System.currentTimeMillis())
                .addAllRegionIds(rsService.getActiveRegionIds())
                .setMetrics(ServerMetrics.newBuilder()
                        .setCpuUsage(35.0)
                        .setMemoryUsage(45.0)
                        .setDiskUsedBytes(512L * 1024 * 1024)
                        .setDiskTotalBytes(10240L * 1024 * 1024)
                        .build())
                .build());
        assertTrue("Heartbeat should be acknowledged", hbResp.getAcknowledged());
    }
}
