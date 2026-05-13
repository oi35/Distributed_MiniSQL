import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.minisql.master.proto.MasterServiceGrpc;
import com.minisql.master.proto.HeartbeatRequest;
import java.util.concurrent.TimeUnit;

public class GrpcTest {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8000;

        System.out.println("Trying to connect to " + host + ":" + port);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        MasterServiceGrpc.MasterServiceBlockingStub stub =
                MasterServiceGrpc.newBlockingStub(channel);

        try {
            var resp = stub.sendHeartbeat(HeartbeatRequest.newBuilder()
                    .setServerId("test-client")
                    .setTimestamp(System.currentTimeMillis())
                    .build());
            System.out.println("SUCCESS: Heartbeat acknowledged=" + resp.getAcknowledged());
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }

        channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
    }
}
