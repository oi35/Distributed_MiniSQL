package com.minisql.client.gateway;

import com.minisql.client.MiniSQLClient;
import com.minisql.client.schema.TableSchemaCache;
import com.minisql.client.sql.SqlExecutor;
import com.minisql.master.proto.ClientMasterServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GatewayServer {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayServer.class);

    private final int port;
    private final String masterAddress;
    private Server server;
    private ManagedChannel masterChannel;
    private MiniSQLClient client;

    public GatewayServer(int port, String masterAddress) {
        this.port = port;
        this.masterAddress = masterAddress;
    }

    public void start() throws IOException {
        masterChannel = ManagedChannelBuilder.forTarget(masterAddress)
                .usePlaintext()
                .build();
        client = new MiniSQLClient(masterChannel, new com.minisql.client.conn.ConnectionManager());
        TableSchemaCache schemas = new TableSchemaCache(
                ClientMasterServiceGrpc.newBlockingStub(masterChannel));
        SqlExecutor executor = new SqlExecutor(client, schemas);

        server = ServerBuilder.forPort(port)
                .addService(new GatewayServiceImpl(executor))
                .build()
                .start();

        LOG.info("gateway listening on {} (master={})", port, masterAddress);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("shutdown hook triggered");
            stop();
        }, "gateway-shutdown"));
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
        if (client != null) {
            client.close();
        }
        if (masterChannel != null) {
            masterChannel.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("gateway.port", "9090"));
        String master = System.getProperty("gateway.master", "localhost:8000");
        GatewayServer server = new GatewayServer(port, master);
        server.start();
        server.blockUntilShutdown();
    }
}
