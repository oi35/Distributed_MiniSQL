import com.google.protobuf.ByteString;
import com.minisql.common.proto.*;
import com.minisql.master.MasterServer;
import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.integration.fixtures.EmbeddedZookeeperServer;
import com.minisql.master.proto.*;
import com.minisql.regionserver.proto.*;
import com.minisql.regionserver.service.RegionServerServiceImpl;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Full demo flow verification:
 * 1. Start ZK + Master + RS-001 + RS-002
 * 2. Admin CLI equivalent: cluster status, cluster nodes, table list, table route
 * 3. RegionServer console equivalent: put, get, delete, exists, list (via gRPC)
 * 4. Verify route table is populated after createTable
 */
public class VerifyDemoFlow {
    static int masterPort = 15000;
    static int rs1Port = 15001;
    static int rs2Port = 15002;
    static String tableName = "users";

    public static void main(String[] args) throws Exception {
        System.out.println("============================================");
        System.out.println("  DEMO FLOW VERIFICATION");
        System.out.println("============================================");

        // 1. Start ZK
        System.out.println("\n--- Step 1: Start ZK ---");
        EmbeddedZookeeperServer zk = new EmbeddedZookeeperServer();
        zk.start();
        System.out.println("  ZK started: " + zk.getConnectString());

        // 2. Start Master
        System.out.println("\n--- Step 2: Start Master ---");
        MasterServer master = new MasterServer(masterPort, "demo-master", zk.getConnectString());
        master.start();
        waitForLeader(master);
        System.out.println("  Master started on port " + masterPort);

        // 3. Start RS-001
        System.out.println("\n--- Step 3: Start RS-001 ---");
        Properties props1 = new Properties();
        props1.setProperty("storage.backend", "memory");
        props1.setProperty("wal.enabled", "false");
        RegionServerServiceImpl rs1 = new RegionServerServiceImpl("rs-001", props1);
        Server rs1Server = ServerBuilder.forPort(rs1Port).addService(rs1).build().start();
        System.out.println("  RS-001 started on port " + rs1Port);

        // 4. Start RS-002
        System.out.println("\n--- Step 4: Start RS-002 ---");
        Properties props2 = new Properties();
        props2.setProperty("storage.backend", "memory");
        props2.setProperty("wal.enabled", "false");
        RegionServerServiceImpl rs2 = new RegionServerServiceImpl("rs-002", props2);
        Server rs2Server = ServerBuilder.forPort(rs2Port).addService(rs2).build().start();
        System.out.println("  RS-002 started on port " + rs2Port);

        // 5. Set up channels
        ManagedChannel masterChan = ManagedChannelBuilder.forAddress("localhost", masterPort).usePlaintext().build();
        ManagedChannel rs1Chan = ManagedChannelBuilder.forAddress("localhost", rs1Port).usePlaintext().build();
        ManagedChannel rs2Chan = ManagedChannelBuilder.forAddress("localhost", rs2Port).usePlaintext().build();

        MasterServiceGrpc.MasterServiceBlockingStub masterStub = MasterServiceGrpc.newBlockingStub(masterChan);
        ClientMasterServiceGrpc.ClientMasterServiceBlockingStub clientStub = ClientMasterServiceGrpc.newBlockingStub(masterChan);
        AdminServiceGrpc.AdminServiceBlockingStub adminStub = AdminServiceGrpc.newBlockingStub(masterChan);
        RegionServerServiceGrpc.RegionServerServiceBlockingStub rs1Stub = RegionServerServiceGrpc.newBlockingStub(rs1Chan);

        // 6. Register RS-001 and RS-002 with Master
        System.out.println("\n--- Step 5: Register RS-001 and RS-002 with Master ---");
        register(masterStub, "rs-001", rs1Port);
        register(masterStub, "rs-002", rs2Port);
        heartbeat(masterStub, "rs-001");
        heartbeat(masterStub, "rs-002");
        Thread.sleep(1000);

        ClusterManager cm = master.getClusterManager();
        System.out.println("  Online servers: " + cm.getOnlineServers().size());
        assert cm.getOnlineServers().size() == 2 : "Expected 2 online servers";

        // ============ ADMIN CLI EQUIVALENT ============

        // 7. cluster status
        System.out.println("\n--- Step 6: cluster status ---");
        GetClusterHealthResponse health = clientStub.getClusterHealth(GetClusterHealthRequest.newBuilder().build());
        System.out.println("  Health: " + health.getStatus());
        System.out.println("  Total Servers: " + health.getTotalServers());
        System.out.println("  Online Servers: " + health.getOnlineServers());
        assert health.getTotalServers() == 2 : "Expected 2 total servers";
        assert health.getOnlineServers() == 2 : "Expected 2 online servers";
        assert health.getStatus() == GetClusterHealthResponse.HealthStatus.HEALTHY : "Expected HEALTHY";
        System.out.println("  PASS: cluster status verified");

        // 8. cluster nodes
        System.out.println("\n--- Step 7: cluster nodes ---");
        ListServersResponse nodes = adminStub.listServers(ListServersRequest.newBuilder().build());
        System.out.println("  Total: " + nodes.getTotalCount() + ", Online: " + nodes.getOnlineCount());
        for (ServerDetail sd : nodes.getServersList()) {
            System.out.println("  Server: " + sd.getServerId() + " " + sd.getAddress()
                + " State: " + sd.getState() + " Load: " + sd.getLoadScore());
        }
        assert nodes.getTotalCount() == 2 : "Expected 2 servers";
        assert nodes.getOnlineCount() == 2 : "Expected 2 online";
        System.out.println("  PASS: cluster nodes verified");

        // 9. table list (initially empty)
        System.out.println("\n--- Step 8: table list (before creation) ---");
        ListTablesResponse tables = clientStub.listTables(ListTablesRequest.newBuilder().build());
        System.out.println("  Tables: " + tables.getTableNamesList());
        assert tables.getTableNamesList().isEmpty() : "Expected no tables initially";
        System.out.println("  PASS: empty table list verified");

        // ============ CREATE TABLE VIA GRPC ============

        // 10. CreateTable via ClientMasterService (the exact RPC from Admin CLI)
        System.out.println("\n--- Step 9: CreateTable via gRPC ---");
        CreateTableResponse createResp = clientStub.createTable(CreateTableRequest.newBuilder()
                .setSchema(TableSchema.newBuilder()
                        .setTableName(tableName)
                        .setPrimaryKey("user_id")
                        .addColumns(ColumnSchema.newBuilder().setName("user_id").setType("VARCHAR").build())
                        .addColumns(ColumnSchema.newBuilder().setName("name").setType("VARCHAR").build())
                        .addColumns(ColumnSchema.newBuilder().setName("age").setType("INT").build())
                        .build())
                .build());
        System.out.println("  CreateTable success: " + createResp.getSuccess());
        assert createResp.getSuccess() : "CreateTable should succeed";
        System.out.println("  PASS: CreateTable verified");

        // 11. table list (after creation)
        System.out.println("\n--- Step 10: table list (after creation) ---");
        tables = clientStub.listTables(ListTablesRequest.newBuilder().build());
        System.out.println("  Tables: " + tables.getTableNamesList());
        assert tables.getTableNamesCount() == 1 : "Expected 1 table";
        assert tables.getTableNamesList().get(0).equals(tableName) : "Expected 'users' table";
        System.out.println("  PASS: table list after creation verified");

        // ============ KEY VERIFICATION: ROUTE TABLE ============

        // 12. table route users (THE CRITICAL FIX)
        System.out.println("\n--- Step 11: table route users (CRITICAL: verify default region) ---");
        GetRouteTableResponse route = clientStub.getRouteTable(GetRouteTableRequest.newBuilder()
                .setTableName(tableName)
                .setCachedVersion(0)
                .build());
        assert route.getSuccess() : "GetRouteTable should succeed";
        assert !route.getCacheValid() : "Should return fresh route data";
        assert route.getRouteTable().getRoutesCount() > 0 : "Route table should have at least 1 region";
        System.out.println("  Route table version: " + route.getRouteTable().getVersion());
        System.out.println("  Regions (" + route.getRouteTable().getRoutesCount() + "):");
        for (RouteEntry entry : route.getRouteTable().getRoutesList()) {
            System.out.println("    Region: " + entry.getRegionId());
            System.out.println("      Range: [\"" + entry.getStartKey().toStringUtf8()
                + "\", \"" + entry.getEndKey().toStringUtf8() + "\")");
            System.out.println("      Primary: " + entry.getPrimaryServer()
                + " (" + entry.getPrimaryAddress() + ")");
            assert !entry.getRegionId().isEmpty() : "Region ID should not be empty";
            assert !entry.getPrimaryServer().isEmpty() : "Primary server should be set";
        }
        System.out.println("  PASS: route table verified (default region was auto-created!)");

        // ============ DATA OPERATIONS VIA RS (simulates RegionServer console) ============

        // 13. Open region on RS-001
        System.out.println("\n--- Step 12: Open region on RS-001 ---");
        rs1.openRegion(RegionInfo.newBuilder()
                .setTableName(tableName)
                .setRegionId("region-001")
                .setPrimaryServer("rs-001")
                .build());
        System.out.println("  PASS: open region verified");

        // 14. PUT data
        System.out.println("\n--- Step 13: PUT data ---");
        putAndVerify(rs1Stub, "user-1001", Map.of("name", "Alice", "age", "30"));
        putAndVerify(rs1Stub, "user-1002", Map.of("name", "Bob", "age", "25"));
        putAndVerify(rs1Stub, "user-1003", Map.of("name", "Charlie", "age", "35"));
        System.out.println("  PASS: PUT operations verified");

        // 15. GET data
        System.out.println("\n--- Step 14: GET data ---");
        getAndVerify(rs1Stub, "user-1001", true);
        getAndVerify(rs1Stub, "nonexistent", false);
        System.out.println("  PASS: GET operations verified");

        // 16. EXISTS
        System.out.println("\n--- Step 15: EXISTS ---");
        existsAndVerify(rs1Stub, "user-1001", true);
        existsAndVerify(rs1Stub, "nonexistent", false);
        System.out.println("  PASS: EXISTS operations verified");

        // 17. SCAN
        System.out.println("\n--- Step 16: SCAN ---");
        Iterator<ScanResponse> scanIter = rs1Stub.scan(ScanRequest.newBuilder()
                .setTableName(tableName).setRegionId("region-001")
                .setStartKey(ByteString.EMPTY).setEndKey(ByteString.EMPTY).setLimit(10)
                .build());
        int count = 0;
        while (scanIter.hasNext()) { scanIter.next(); count++; }
        System.out.println("  Scanned " + count + " rows (expected 3)");
        assert count == 3 : "Expected 3 rows";
        System.out.println("  PASS: SCAN verified");

        // 18. SCAN with limit
        System.out.println("\n--- Step 17: SCAN with limit ---");
        scanIter = rs1Stub.scan(ScanRequest.newBuilder()
                .setTableName(tableName).setRegionId("region-001")
                .setStartKey(ByteString.EMPTY).setEndKey(ByteString.EMPTY).setLimit(2)
                .build());
        count = 0;
        while (scanIter.hasNext()) { scanIter.next(); count++; }
        System.out.println("  Scanned " + count + " rows (limit 2)");
        assert count == 2 : "Expected 2 rows with limit";
        System.out.println("  PASS: SCAN limit verified");

        // 19. DELETE
        System.out.println("\n--- Step 18: DELETE ---");
        deleteAndVerify(rs1Stub, "user-1003", true);
        getAndVerify(rs1Stub, "user-1003", false);
        getAndVerify(rs1Stub, "user-1001", true);
        System.out.println("  PASS: DELETE verified");

        // 20. SCAN on unopened region (our onError -> onCompleted fix)
        System.out.println("\n--- Step 19: SCAN on unopened region (Fix 3 verification) ---");
        scanIter = rs1Stub.scan(ScanRequest.newBuilder()
                .setTableName(tableName).setRegionId("nonexistent-region")
                .setStartKey(ByteString.EMPTY).setEndKey(ByteString.EMPTY).setLimit(10)
                .build());
        count = 0;
        while (scanIter.hasNext()) { scanIter.next(); count++; }
        System.out.println("  Scanned on unopened region: " + count + " rows (expected 0, no error)");
        assert count == 0 : "Expected 0 rows on unopened region";
        System.out.println("  PASS: SCAN error handling verified");

        System.out.println("\n============================================");
        System.out.println("  ALL DEMO FLOW VERIFICATIONS PASSED!");
        System.out.println("============================================");

        // Cleanup
        rs1Chan.shutdown(); rs2Chan.shutdown(); masterChan.shutdown();
        rs1Server.shutdown(); rs2Server.shutdown();
        master.stop(); zk.stop();
    }

    static void register(MasterServiceGrpc.MasterServiceBlockingStub stub, String id, int port) {
        stub.registerRegionServer(RegisterRegionServerRequest.newBuilder()
                .setServerId(id).setHost("localhost").setPort(port)
                .setTotalMemoryMb(1024).setCpuCores(2).setDiskCapacityMb(10240)
                .build());
    }

    static void heartbeat(MasterServiceGrpc.MasterServiceBlockingStub stub, String id) {
        stub.sendHeartbeat(HeartbeatRequest.newBuilder()
                .setServerId(id).setTimestamp(System.currentTimeMillis())
                .setMetrics(ServerMetrics.newBuilder().setCpuUsage(20).setMemoryUsage(30)
                        .setDiskUsedBytes(512 * 1024 * 1024L).setDiskTotalBytes(10240 * 1024 * 1024L).build())
                .build());
    }

    static void waitForLeader(MasterServer master) throws Exception {
        for (int i = 0; i < 30; i++) {
            if (master.isLeader()) return;
            Thread.sleep(500);
        }
        throw new RuntimeException("Master did not become leader");
    }

    static void putAndVerify(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub, String key, Map<String, String> cols) {
        PutRequest.Builder b = PutRequest.newBuilder()
                .setTableName(tableName).setRegionId("region-001")
                .setKey(ByteString.copyFromUtf8(key));
        cols.forEach((k, v) -> b.putColumns(k, ByteString.copyFromUtf8(v)));
        PutResponse r = stub.put(b.build());
        assert r.getSuccess() : "PUT failed for " + key;
        System.out.println("  PUT " + key + ": OK");
    }

    static void getAndVerify(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub, String key, boolean expectFound) {
        GetResponse r = stub.get(GetRequest.newBuilder()
                .setTableName(tableName).setRegionId("region-001")
                .setKey(ByteString.copyFromUtf8(key)).build());
        System.out.println("  GET " + key + ": " + (r.getFound() ? "FOUND" : "NOT FOUND"));
        assert r.getFound() == expectFound : "Expected found=" + expectFound + " for " + key;
    }

    static void existsAndVerify(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub, String key, boolean expectExists) {
        ExistsResponse r = stub.exists(ExistsRequest.newBuilder()
                .setTableName(tableName).setRegionId("region-001")
                .setKey(ByteString.copyFromUtf8(key)).build());
        System.out.println("  EXISTS " + key + ": " + (r.getExists() ? "YES" : "NO"));
        assert r.getExists() == expectExists : "Expected exists=" + expectExists + " for " + key;
    }

    static void deleteAndVerify(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub, String key, boolean expectExisted) {
        com.minisql.regionserver.proto.DeleteResponse r = stub.delete(
                com.minisql.regionserver.proto.DeleteRequest.newBuilder()
                        .setTableName(tableName).setRegionId("region-001")
                        .setKey(ByteString.copyFromUtf8(key)).build());
        System.out.println("  DELETE " + key + ": OK (existed: " + r.getExisted() + ")");
        assert r.getExisted() == expectExisted : "Expected existed=" + expectExisted + " for " + key;
    }
}
