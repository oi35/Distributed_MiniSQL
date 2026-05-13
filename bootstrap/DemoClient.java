import com.google.protobuf.ByteString;
import com.minisql.common.proto.RegionInfo;
import com.minisql.regionserver.proto.*;
import io.grpc.ManagedChannelBuilder;
import java.util.Map;

/**
 * 演示客户端：通过 RegionServerService gRPC 接口操作数据。
 * 演示流程：打开 Region -> CRUD 操作。
 */
public class DemoClient {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8001;
        String cmd = args.length > 2 ? args[2] : "all";

        var channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext().build();
        var stub = RegionServerServiceGrpc.newBlockingStub(channel);

        if (cmd.equals("open")) {
            // Open a region first
            String table = args.length > 3 ? args[3] : "users";
            String regionId = args.length > 4 ? args[4] : "region-001";
            openRegion(stub, table, regionId);
        } else if (cmd.equals("all")) {
            doFullDemo(stub);
        } else {
            switch (cmd) {
                case "put": doPut(stub, args); break;
                case "get": doGet(stub, args); break;
                case "delete": doDelete(stub, args); break;
                case "exists": doExists(stub, args); break;
                case "scan": doScan(stub, args); break;
                default: System.out.println("Commands: put, get, delete, exists, scan, open, all");
            }
        }

        channel.shutdown();
    }

    static void openRegion(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub,
                          String table, String regionId) {
        var response = stub.openRegion(OpenRegionRequest.newBuilder()
                .setRegion(RegionInfo.newBuilder()
                        .setRegionId(regionId)
                        .setTableName(table)
                        .setPrimaryServer("rs-001")
                        .build())
                .build());
        System.out.println("OPEN REGION " + regionId + " for table " + table + ": "
                + (response.getSuccess() ? "SUCCESS" : "FAILED"));
    }

    static void doPut(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub, String[] args) {
        String table = args.length > 3 ? args[3] : "users";
        String key = args.length > 4 ? args[4] : "user-1001";
        String regionId = args.length > 5 ? args[5] : "region-001";

        var builder = PutRequest.newBuilder()
                .setTableName(table)
                .setRegionId(regionId)
                .setKey(ByteString.copyFromUtf8(key));

        if (args.length > 6) {
            for (int i = 6; i < args.length; i++) {
                String[] parts = args[i].split("=", 2);
                if (parts.length == 2) {
                    builder.putColumns(parts[0], ByteString.copyFromUtf8(parts[1]));
                }
            }
        } else {
            builder.putColumns("name", ByteString.copyFromUtf8("Alice"));
            builder.putColumns("age", ByteString.copyFromUtf8("30"));
            builder.putColumns("email", ByteString.copyFromUtf8("alice@test.com"));
        }

        var response = stub.put(builder.build());
        System.out.println("PUT " + key + ": " + (response.getSuccess() ? "OK" : "FAILED"));
    }

    static void doGet(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub, String[] args) {
        String table = args.length > 3 ? args[3] : "users";
        String key = args.length > 4 ? args[4] : "user-1001";
        String regionId = args.length > 5 ? args[5] : "region-001";

        var response = stub.get(GetRequest.newBuilder()
                .setTableName(table)
                .setRegionId(regionId)
                .setKey(ByteString.copyFromUtf8(key))
                .build());

        if (response.getFound()) {
            System.out.print("GET " + key + " ->");
            response.getColumnsMap().forEach((col, val) ->
                    System.out.print(" " + col + "=" + val.toStringUtf8()));
            System.out.println();
        } else {
            System.out.println("GET " + key + " -> NOT FOUND");
        }
    }

    static void doDelete(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub, String[] args) {
        String table = args.length > 3 ? args[3] : "users";
        String key = args.length > 4 ? args[4] : "user-1003";
        String regionId = args.length > 5 ? args[5] : "region-001";

        var response = stub.delete(DeleteRequest.newBuilder()
                .setTableName(table)
                .setRegionId(regionId)
                .setKey(ByteString.copyFromUtf8(key))
                .build());

        System.out.println("DELETE " + key + ": " + (response.getSuccess() ? "OK" : "FAILED")
                + " (key " + (response.getExisted() ? "existed" : "not found") + ")");
    }

    static void doExists(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub, String[] args) {
        String table = args.length > 3 ? args[3] : "users";
        String key = args.length > 4 ? args[4] : "user-1001";
        String regionId = args.length > 5 ? args[5] : "region-001";

        var response = stub.exists(ExistsRequest.newBuilder()
                .setTableName(table)
                .setRegionId(regionId)
                .setKey(ByteString.copyFromUtf8(key))
                .build());

        System.out.println("EXISTS " + key + ": " + (response.getExists() ? "YES" : "NO"));
    }

    static void doScan(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub, String[] args) {
        String table = args.length > 3 ? args[3] : "users";
        int limit = args.length > 4 ? Integer.parseInt(args[4]) : 20;
        String regionId = args.length > 5 ? args[5] : "region-001";

        var response = stub.scan(ScanRequest.newBuilder()
                .setTableName(table)
                .setRegionId(regionId)
                .setStartKey(ByteString.EMPTY)
                .setEndKey(ByteString.EMPTY)
                .setLimit(limit)
                .build());

        System.out.println("SCAN " + table + " (region=" + regionId + ", limit=" + limit + "):");
        int count = 0;
        while (response.hasNext()) {
            var row = response.next();
            System.out.print("  " + row.getKey().toStringUtf8());
            row.getColumnsMap().forEach((col, val) ->
                    System.out.print(" " + col + "=" + val.toStringUtf8()));
            System.out.println();
            count++;
        }
        if (count == 0) System.out.println("  (no rows)");
    }

    static void doFullDemo(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub) {
        String table = "users";
        String region = "region-001";

        System.out.println("============================================");
        System.out.println("  Step 1: Open Region");
        System.out.println("============================================");
        openRegion(stub, table, region);

        System.out.println();
        System.out.println("============================================");
        System.out.println("  Step 2: PUT - Insert 3 rows");
        System.out.println("============================================");
        put(stub, table, region, "user-1001", Map.of("name", "Alice", "age", "30", "email", "alice@test.com"));
        put(stub, table, region, "user-1002", Map.of("name", "Bob", "age", "25", "email", "bob@test.com"));
        put(stub, table, region, "user-1003", Map.of("name", "Charlie", "age", "35", "email", "charlie@test.com"));

        System.out.println();
        System.out.println("============================================");
        System.out.println("  Step 3: GET - Retrieve by key");
        System.out.println("============================================");
        get(stub, table, region, "user-1001");
        get(stub, table, region, "nonexistent");

        System.out.println();
        System.out.println("============================================");
        System.out.println("  Step 4: EXISTS - Check key existence");
        System.out.println("============================================");
        exists(stub, table, region, "user-1001");
        exists(stub, table, region, "nonexistent");

        System.out.println();
        System.out.println("============================================");
        System.out.println("  Step 5: SCAN - List all rows");
        System.out.println("============================================");
        scan(stub, table, region, 10);

        System.out.println();
        System.out.println("============================================");
        System.out.println("  Step 6: DELETE - Remove a row");
        System.out.println("============================================");
        delete(stub, table, region, "user-1003");
        System.out.println("  Verify:");
        exists(stub, table, region, "user-1003");
        exists(stub, table, region, "user-1001");

        System.out.println();
        System.out.println("============================================");
        System.out.println("  All CRUD operations completed successfully");
        System.out.println("============================================");
    }

    static void put(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub,
                    String table, String region, String key, Map<String, String> columns) {
        var builder = PutRequest.newBuilder()
                .setTableName(table).setRegionId(region)
                .setKey(ByteString.copyFromUtf8(key));
        columns.forEach((col, val) ->
                builder.putColumns(col, ByteString.copyFromUtf8(val)));
        var r = stub.put(builder.build());
        System.out.println("  PUT " + key + ": " + (r.getSuccess() ? "OK" : "FAILED"));
    }

    static void get(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub,
                    String table, String region, String key) {
        var r = stub.get(GetRequest.newBuilder()
                .setTableName(table).setRegionId(region)
                .setKey(ByteString.copyFromUtf8(key))
                .build());
        if (r.getFound()) {
            System.out.print("  GET " + key + " ->");
            r.getColumnsMap().forEach((col, val) ->
                    System.out.print(" " + col + "=" + val.toStringUtf8()));
            System.out.println();
        } else {
            System.out.println("  GET " + key + " -> NOT FOUND");
        }
    }

    static void exists(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub,
                       String table, String region, String key) {
        var r = stub.exists(ExistsRequest.newBuilder()
                .setTableName(table).setRegionId(region)
                .setKey(ByteString.copyFromUtf8(key))
                .build());
        System.out.println("  EXISTS " + key + ": " + (r.getExists() ? "YES" : "NO"));
    }

    static void delete(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub,
                       String table, String region, String key) {
        var r = stub.delete(DeleteRequest.newBuilder()
                .setTableName(table).setRegionId(region)
                .setKey(ByteString.copyFromUtf8(key))
                .build());
        System.out.println("  DELETE " + key + ": " + (r.getSuccess() ? "OK" : "FAILED"));
    }

    static void scan(RegionServerServiceGrpc.RegionServerServiceBlockingStub stub,
                     String table, String region, int limit) {
        var r = stub.scan(ScanRequest.newBuilder()
                .setTableName(table).setRegionId(region)
                .setStartKey(ByteString.EMPTY)
                .setEndKey(ByteString.EMPTY)
                .setLimit(limit)
                .build());
        int count = 0;
        while (r.hasNext()) {
            var row = r.next();
            System.out.print("  " + row.getKey().toStringUtf8());
            row.getColumnsMap().forEach((col, val) ->
                    System.out.print(" " + col + "=" + val.toStringUtf8()));
            System.out.println();
            count++;
        }
        if (count == 0) System.out.println("  (no rows)");
    }
}
