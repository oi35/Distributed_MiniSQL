package com.minisql.regionserver;

import com.minisql.regionserver.service.RegionServerServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * RegionServer main entry with gRPC server and a small CLI for local testing.
 */
public class RegionServerMain {

    private static final Logger logger = LoggerFactory.getLogger(RegionServerMain.class);
    private static final int DEFAULT_PORT = 8001;

    private final String regionServerId;
    private final int port;
    private final RegionServerServiceImpl service;
    private Server grpcServer;

    public RegionServerMain(String regionServerId, int port, Properties properties) {
        this.regionServerId = regionServerId;
        this.port = port;
        this.service = new RegionServerServiceImpl(regionServerId, properties);
        logger.info("RegionServer {} initialized on port {}", regionServerId, port);
    }

    public void start() throws IOException {
        grpcServer = ServerBuilder.forPort(port)
                .addService(service)
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

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
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
        logger.info("RegionServer {} stopped", regionServerId);
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
        if (parts.length != 2) {
            System.out.println("Usage: list <table>");
            return;
        }
        System.out.println("List command is reserved for a future scan/list implementation");
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
            regionServer.runCommandLine();
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
