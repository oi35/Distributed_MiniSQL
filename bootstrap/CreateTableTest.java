import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.TableSchema;
import com.minisql.master.proto.ClientMasterServiceGrpc;
import com.minisql.master.proto.CreateTableRequest;
import com.minisql.master.proto.CreateTableResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;

public class CreateTableTest {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8000;
        String tableName = args.length > 2 ? args[2] : "users";

        System.out.println("Creating table '" + tableName + "' on " + host + ":" + port);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        try {
            ClientMasterServiceGrpc.ClientMasterServiceBlockingStub stub =
                    ClientMasterServiceGrpc.newBlockingStub(channel);

            TableSchema schema = TableSchema.newBuilder()
                    .setTableName(tableName)
                    .setPrimaryKey("row_key")
                    .setVersion(1)
                    .setCreateTime(System.currentTimeMillis())
                    .addColumns(ColumnSchema.newBuilder()
                            .setName("name")
                            .setType("VARCHAR(100)")
                            .setNullable(true)
                            .build())
                    .addColumns(ColumnSchema.newBuilder()
                            .setName("age")
                            .setType("BIGINT")
                            .setNullable(true)
                            .build())
                    .addColumns(ColumnSchema.newBuilder()
                            .setName("email")
                            .setType("VARCHAR(200)")
                            .setNullable(true)
                            .build())
                    .build();

            CreateTableResponse response = stub.createTable(
                    CreateTableRequest.newBuilder()
                            .setSchema(schema)
                            .setInitialRegionCount(1)
                            .build());

            System.out.println("CreateTable result:");
            System.out.println("  Success: " + response.getSuccess());
            System.out.println("  Regions: " + response.getInitialRegionsCount());
            System.out.println("  Route version: " + response.getRouteTableVersion());
            if (!response.getSuccess()) {
                System.out.println("  Error: " + response.getErrorMessage());
            }

        } finally {
            channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        }
    }
}
