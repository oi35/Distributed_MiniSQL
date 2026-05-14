import com.minisql.master.proto.ClientMasterServiceGrpc;
import com.minisql.master.proto.DropTableRequest;
import com.minisql.master.proto.DropTableResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;

public class DropTableTest {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8000;
        String tableName = args.length > 2 ? args[2] : "users";

        System.out.println("Dropping table '" + tableName + "' on " + host + ":" + port);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        try {
            ClientMasterServiceGrpc.ClientMasterServiceBlockingStub stub =
                    ClientMasterServiceGrpc.newBlockingStub(channel);

            DropTableResponse response = stub.dropTable(
                    DropTableRequest.newBuilder()
                            .setTableName(tableName)
                            .setForce(true)
                            .build());

            System.out.println("DropTable result:");
            System.out.println("  Success: " + response.getSuccess());
            System.out.println("  Regions dropped: " + response.getRegionsDropped());
            if (!response.getSuccess()) {
                System.out.println("  Error: " + response.getErrorMessage());
            }

        } finally {
            channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        }
    }
}
