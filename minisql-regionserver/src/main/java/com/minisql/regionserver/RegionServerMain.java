package com.minisql.regionserver;

import com.minisql.common.proto.RegionInfo;
import com.minisql.common.proto.ServerMetrics;
import com.minisql.master.proto.HeartbeatRequest;
import com.minisql.master.proto.HeartbeatResponse;
import com.minisql.master.proto.MasterServiceGrpc;
import com.minisql.master.proto.RegionCommand;
import com.minisql.master.proto.RegisterRegionServerRequest;
import com.minisql.master.proto.RegisterRegionServerResponse;
import com.minisql.master.proto.UnregisterRegionServerRequest;
import com.minisql.regionserver.service.RegionServerServiceImpl;
import com.minisql.regionserver.store.RegionDataStore;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * RegionServer main entry with gRPC server and a small CLI for local testing.
 */
public class RegionServerMain {

    private static final Logger logger = LoggerFactory.getLogger(RegionServerMain.class);
    private static final int DEFAULT_PORT = 8001;

    private final String regionServerId;
    private final int port;
    private final String host;
    private final String masterHost;
    private final int masterPort;
    private final boolean masterRegistrationEnabled;
    private final RegionServerServiceImpl service;
    private Server grpcServer;
    private ManagedChannel masterChannel;
    private MasterServiceGrpc.MasterServiceBlockingStub masterStub;
    private ScheduledExecutorService heartbeatExecutor;
    private volatile boolean registered;
    private volatile String assignedServerId;
    private volatile int heartbeatIntervalMs;

    public RegionServerMain(String regionServerId, int port, Properties properties) {
        this.regionServerId = regionServerId;
        this.assignedServerId = regionServerId;
        this.port = port;
        this.host = properties.getProperty("regionserver.host", "localhost");
        this.masterHost = properties.getProperty("master.host", "localhost");
        this.masterPort = parsePort(properties.getProperty("master.port"), 8000);
        this.masterRegistrationEnabled = Boolean.parseBoolean(
                properties.getProperty("master.registration.enabled", "true"));
        this.heartbeatIntervalMs = parsePort(
                properties.getProperty("master.heartbeat.interval.ms"), 3000);
        this.service = new RegionServerServiceImpl(regionServerId, properties);
        logger.info("RegionServer {} initialized on port {}", regionServerId, port);
    }

    public void start() throws IOException {
        grpcServer = ServerBuilder.forPort(port)
                .addService(service)
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        connectToMaster();

        logger.info("RegionServer {} started on port {}", regionServerId, port);
        System.out.println("=====================================");
        System.out.println("  RegionServer " + regionServerId + " Started on port " + port);
        System.out.println("=====================================");
        System.out.println("Available commands:");
        System.out.println("  put <table> <key> <column>=<value> [column2=value2...]");
        System.out.println("  get <table> <key>");
        System.out.println("  delete <table> <key>");
        System.out.println("  exists <table> <key>");
        System.out.println("  list <table>");
        System.out.println("  exit");
        System.out.println("=====================================");
    }

    public void stop() {
        stopHeartbeat();
        unregisterFromMaster();
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
        if (masterChannel != null) {
            masterChannel.shutdown();
        }
        logger.info("RegionServer {} stopped", regionServerId);
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }

    public void runCommandLine() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        try {
            while (true) {
                System.out.print("RegionServer> ");
                String line = reader.readLine();
                if (line == null || line.trim().equals("exit")) {
                    break;
                }

                try {
                    processCommand(line.trim());
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Error reading input", e);
        } finally {
            stop();
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length == 0) {
            return;
        }

        String cmd = parts[0].toLowerCase();
        switch (cmd) {
            case "put":
                handlePut(parts);
                break;
            case "get":
                handleGet(parts);
                break;
            case "delete":
                handleDelete(parts);
                break;
            case "exists":
                handleExists(parts);
                break;
            case "list":
                handleList(parts);
                break;
            case "open":
                handleOpen(parts);
                break;
            case "close":
                handleClose(parts);
                break;
            default:
                System.out.println("Unknown command: " + cmd);
                break;
        }
    }

    private void handlePut(String[] parts) {
        if (parts.length < 4) {
            System.out.println("Usage: put <table> <key> <column>=<value> [column2=value2...]");
            return;
        }

        String table = parts[1];
        String key = parts[2];
        Map<String, byte[]> columns = new HashMap<>();
        for (int i = 3; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            if (kv.length == 2) {
                columns.put(kv[0], kv[1].getBytes(StandardCharsets.UTF_8));
            }
        }

        boolean success = service.put(table, "region-001", key, columns);
        System.out.println(success ? "PUT successful" : "PUT failed");
    }

    private void handleGet(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Usage: get <table> <key>");
            return;
        }

        Map<String, byte[]> result = service.get(parts[1], "region-001", parts[2]);
        if (result == null) {
            System.out.println("Key not found");
            return;
        }

        System.out.println("Data:");
        for (Map.Entry<String, byte[]> entry : result.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + new String(entry.getValue(), StandardCharsets.UTF_8));
        }
    }

    private void handleDelete(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Usage: delete <table> <key>");
            return;
        }

        boolean existed = service.delete(parts[1], "region-001", parts[2]);
        System.out.println(existed ? "DELETE successful (key existed)" : "DELETE: key not found");
    }

    private void handleExists(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Usage: exists <table> <key>");
            return;
        }

        boolean exists = service.exists(parts[1], "region-001", parts[2]);
        System.out.println(exists ? "Key exists" : "Key not found");
    }

    private void handleList(String[] parts) {
        if (parts.length < 2 || parts.length > 3) {
            System.out.println("Usage: list <table> [limit]");
            return;
        }
        int limit = parts.length == 3 ? parsePort(parts[2], 20) : 20;
        for (RegionDataStore.StoredRowRecord row : service.listRows("region-001", limit)) {
            System.out.print(new String(row.getKey(), StandardCharsets.UTF_8));
            for (Map.Entry<String, com.google.protobuf.ByteString> entry : row.getColumns().entrySet()) {
                System.out.print(" " + entry.getKey() + "=" + entry.getValue().toStringUtf8());
            }
            System.out.println();
        }
    }

    private void handleOpen(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Usage: open <table> <region>");
            return;
        }
        service.openRegion(RegionInfo.newBuilder()
                .setTableName(parts[1])
                .setRegionId(parts[2])
                .setPrimaryServer(assignedServerId)
                .build());
        System.out.println("Region opened: " + parts[2]);
    }

    private void handleClose(String[] parts) {
        if (parts.length != 2) {
            System.out.println("Usage: close <region>");
            return;
        }
        System.out.println(service.closeRegion(parts[1]) ? "Region closed" : "Region not found");
    }

    private void connectToMaster() {
        if (!masterRegistrationEnabled) {
            logger.info("Master registration disabled");
            return;
        }

        masterChannel = ManagedChannelBuilder.forAddress(masterHost, masterPort)
                .usePlaintext()
                .build();
        masterStub = MasterServiceGrpc.newBlockingStub(masterChannel);

        try {
            RegisterRegionServerResponse response = masterStub.registerRegionServer(
                    RegisterRegionServerRequest.newBuilder()
                            .setServerId(regionServerId)
                            .setHost(host)
                            .setPort(port)
                            .setTotalMemoryMb(Runtime.getRuntime().maxMemory() / 1024 / 1024)
                            .setCpuCores(Runtime.getRuntime().availableProcessors())
                            .setDiskCapacityMb(new File(".").getTotalSpace() / 1024 / 1024)
                            .build());
            if (!response.getSuccess()) {
                logger.warn("RegionServer registration rejected: {}", response.getErrorMessage());
                return;
            }
            registered = true;
            assignedServerId = response.getAssignedServerId().isEmpty()
                    ? regionServerId
                    : response.getAssignedServerId();
            if (response.getHeartbeatIntervalMs() > 0) {
                heartbeatIntervalMs = response.getHeartbeatIntervalMs();
            }
            startHeartbeat();
            logger.info("Registered RegionServer {} with Master {}:{}", assignedServerId, masterHost, masterPort);
        } catch (StatusRuntimeException e) {
            logger.warn("Master {}:{} unavailable, RegionServer will run locally: {}",
                    masterHost, masterPort, e.getStatus());
        }
    }

    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "regionserver-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeatSafely,
                0, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
    }

    private void sendHeartbeatSafely() {
        if (!registered || masterStub == null) {
            return;
        }
        try {
            HeartbeatResponse response = masterStub.sendHeartbeat(HeartbeatRequest.newBuilder()
                    .setServerId(assignedServerId)
                    .setTimestamp(System.currentTimeMillis())
                    .addAllRegionIds(service.getActiveRegionIds())
                    .setMetrics(ServerMetrics.newBuilder()
                            .setRegionCount(service.getActiveRegionIds().size())
                            .setTotalSizeBytes(service.getTotalSizeBytes())
                            .setMemoryUsage(calculateMemoryUsage())
                            .setDiskUsedBytes(new File(".").getTotalSpace() - new File(".").getFreeSpace())
                            .setDiskTotalBytes(new File(".").getTotalSpace())
                            .build())
                    .build());
            if (response.getAcknowledged()) {
                for (RegionCommand command : response.getCommandsList()) {
                    handleRegionCommand(command);
                }
            }
        } catch (Exception e) {
            logger.warn("Heartbeat to Master failed: {}", e.getMessage());
        }
    }

    private void handleRegionCommand(RegionCommand command) {
        switch (command.getType()) {
            case OPEN_REGION:
                service.openRegion(command.getRegionInfo());
                break;
            case CLOSE_REGION:
                service.closeRegion(command.getRegionId());
                break;
            case MIGRATE_REGION:
                service.migrateRegion(command.getRegionId(), command.getTargetServer(), "");
                break;
            default:
                logger.warn("Unsupported region command: {}", command.getType());
                break;
        }
    }

    private void unregisterFromMaster() {
        if (!registered || masterStub == null) {
            return;
        }
        try {
            masterStub.unregisterRegionServer(UnregisterRegionServerRequest.newBuilder()
                    .setServerId(assignedServerId)
                    .setReason("SHUTDOWN")
                    .build());
        } catch (Exception e) {
            logger.warn("Failed to unregister RegionServer from Master: {}", e.getMessage());
        } finally {
            registered = false;
        }
    }

    private double calculateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0) {
            return 0D;
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        return used * 100D / max;
    }

    public static void main(String[] args) {
        Properties properties = loadProperties();
        String regionServerId = properties.getProperty("regionserver.id", "rs-001");
        int port = parsePort(properties.getProperty("regionserver.port"), DEFAULT_PORT);

        if (args.length >= 1) {
            regionServerId = args[0];
        }
        if (args.length >= 2) {
            port = parsePort(args[1], port);
        }

        String idEnv = System.getenv("REGIONSERVER_ID");
        if (idEnv != null && !idEnv.isEmpty()) {
            regionServerId = idEnv;
        }
        String portEnv = System.getenv("REGIONSERVER_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            port = parsePort(portEnv, port);
        }

        logger.info("Starting RegionServer {} on port {}", regionServerId, port);
        RegionServerMain regionServer = new RegionServerMain(regionServerId, port, properties);
        try {
            regionServer.start();
            if (Boolean.parseBoolean(properties.getProperty("regionserver.cli.enabled", "false"))
                    || System.console() != null) {
                regionServer.runCommandLine();
            } else {
                regionServer.blockUntilShutdown();
            }
        } catch (Exception e) {
            logger.error("RegionServer {} failed", regionServerId, e);
            System.exit(1);
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = RegionServerMain.class.getClassLoader()
                .getResourceAsStream("regionserver.conf")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException e) {
            logger.warn("Failed to load regionserver.conf, using defaults", e);
        }
        return properties;
    }

    private static int parsePort(String value, int fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            logger.warn("Invalid port {}, using {}", value, fallback);
            return fallback;
        }
    }
}
