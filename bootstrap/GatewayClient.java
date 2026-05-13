import com.minisql.client.gateway.proto.*;
import io.grpc.ManagedChannelBuilder;

/**
 * 演示 GatewayServer（端口 9090）多语言 gRPC 接口。
 * GatewayService 是语言无关的 protobuf/gRPC 协议，
 * Python/C++/Java 均可通过此接口执行 SQL。
 */
public class GatewayClient {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9090;

        var channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext().build();
        var stub = GatewayServiceGrpc.newBlockingStub(channel);

        System.out.println("============================================");
        System.out.println("  Multi-Language Gateway Test");
        System.out.println("  Connecting to " + host + ":" + port);
        System.out.println("============================================");

        // 1. PING
        System.out.println("\n--- Ping ---");
        var pong = stub.ping(PingRequest.newBuilder().build());
        System.out.println("  Version: " + pong.getVersion());
        System.out.println("  Server Time: " + pong.getServerTimeMs());

        // 2. Execute INSERT
        System.out.println("\n--- Insert ---");
        var insertResp = stub.execute(ExecuteRequest.newBuilder()
                .setSql("INSERT INTO users (user_id, username) VALUES (1, 'alice')")
                .build());
        System.out.println("  INSERT success=" + insertResp.getSuccess()
                + " affected_rows=" + insertResp.getUpdate().getAffectedRows());

        // 3. Execute INSERT again
        insertResp = stub.execute(ExecuteRequest.newBuilder()
                .setSql("INSERT INTO users (user_id, username) VALUES (2, 'bob')")
                .build());
        System.out.println("  INSERT success=" + insertResp.getSuccess()
                + " affected_rows=" + insertResp.getUpdate().getAffectedRows());

        // 4. Execute SELECT
        System.out.println("\n--- Select ---");
        var selectResp = stub.execute(ExecuteRequest.newBuilder()
                .setSql("SELECT user_id, username FROM users WHERE user_id = 1")
                .build());
        if (selectResp.getSuccess()) {
            var query = selectResp.getQuery();
            System.out.print("  Columns: " + String.join(", ", query.getColumnsList()));
            System.out.println();
            for (Row row : query.getRowsList()) {
                System.out.print("  Row: ");
                for (int i = 0; i < query.getColumnsCount(); i++) {
                    TypedValue v = row.getValues(i);
                    System.out.print(query.getColumns(i) + "=");
                    switch (v.getValueCase()) {
                        case INT_VALUE: System.out.print(v.getIntValue()); break;
                        case STRING_VALUE: System.out.print(v.getStringValue()); break;
                        case DOUBLE_VALUE: System.out.print(v.getDoubleValue()); break;
                        case BOOL_VALUE: System.out.print(v.getBoolValue()); break;
                        default: System.out.print("NULL");
                    }
                    System.out.print(" ");
                }
                System.out.println();
            }
        } else {
            System.out.println("  SELECT failed: " + selectResp.getErrorCode()
                    + " - " + selectResp.getErrorMessage());
        }

        // 5. Scan all
        System.out.println("\n--- Scan All ---");
        var scanResp = stub.execute(ExecuteRequest.newBuilder()
                .setSql("SELECT user_id, username FROM users")
                .build());
        if (scanResp.getSuccess()) {
            var query = scanResp.getQuery();
            System.out.println("  Rows: " + query.getRowsCount());
            for (Row row : query.getRowsList()) {
                System.out.print("  - ");
                for (int i = 0; i < query.getColumnsCount(); i++) {
                    var v = row.getValues(i);
                    System.out.print(query.getColumns(i) + "=");
                    switch (v.getValueCase()) {
                        case INT_VALUE: System.out.print(v.getIntValue()); break;
                        case STRING_VALUE: System.out.print(v.getStringValue()); break;
                        case DOUBLE_VALUE: System.out.print(v.getDoubleValue()); break;
                        case BOOL_VALUE: System.out.print(v.getBoolValue()); break;
                        default: System.out.print("NULL");
                    }
                    System.out.print(" ");
                }
                System.out.println();
            }
        } else {
            System.out.println("  SCAN failed: " + scanResp.getErrorCode()
                    + " - " + scanResp.getErrorMessage());
        }

        System.out.println("\n============================================");
        System.out.println("  Gateway test completed");
        System.out.println("  (This same protocol works from Python, C++, etc.)");
        System.out.println("============================================");

        channel.shutdown();
    }
}
