package com.minisql.regionserver;

import com.minisql.regionserver.service.RegionServerServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * RegionServer主类
 *
 * 启动RegionServer并提供命令行界面进行测试
 */
public class RegionServerMain {

    private static final Logger logger = LoggerFactory.getLogger(RegionServerMain.class);

    private final String regionServerId;
    private final RegionServerServiceImpl service;

    public RegionServerMain(String regionServerId) {
        this.regionServerId = regionServerId;
        this.service = new RegionServerServiceImpl(regionServerId);
        logger.info("RegionServer {} initialized", regionServerId);
    }

    /**
     * 启动RegionServer
     */
    public void start() {
        logger.info("RegionServer {} started", regionServerId);
        System.out.println("=====================================");
        System.out.println("  RegionServer " + regionServerId + " Started");
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

    /**
     * 停止RegionServer
     */
    public void stop() {
        logger.info("RegionServer {} stopped", regionServerId);
    }

    /**
     * 运行命令行界面
     */
    public void runCommandLine() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

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

    /**
     * 处理命令
     */
    private void processCommand(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length == 0) return;

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
                columns.put(kv[0], kv[1].getBytes());
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

        String table = parts[1];
        String key = parts[2];

        Map<String, byte[]> result = service.get(table, "region-001", key);
        if (result == null) {
            System.out.println("Key not found");
        } else {
            System.out.println("Data:");
            for (Map.Entry<String, byte[]> entry : result.entrySet()) {
                System.out.println("  " + entry.getKey() + " = " + new String(entry.getValue()));
            }
        }
    }

    private void handleDelete(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Usage: delete <table> <key>");
            return;
        }

        String table = parts[1];
        String key = parts[2];

        boolean existed = service.delete(table, "region-001", key);
        System.out.println(existed ? "DELETE successful (key existed)" : "DELETE: key not found");
    }

    private void handleExists(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Usage: exists <table> <key>");
            return;
        }

        String table = parts[1];
        String key = parts[2];

        boolean exists = service.exists(table, "region-001", key);
        System.out.println(exists ? "Key exists" : "Key not found");
    }

    private void handleList(String[] parts) {
        if (parts.length != 2) {
            System.out.println("Usage: list <table>");
            return;
        }

        String table = parts[1];
        System.out.println("List command not implemented yet");
        // TODO: 实现列出表中所有key的功能
    }

    /**
     * 主入口
     */
    public static void main(String[] args) {
        // 默认值
        String regionServerId = "rs-001";

        // 从命令行参数读取
        if (args.length >= 1) {
            regionServerId = args[0];
        }

        // 从环境变量读取
        String idEnv = System.getenv("REGIONSERVER_ID");
        if (idEnv != null && !idEnv.isEmpty()) {
            regionServerId = idEnv;
        }

        logger.info("Starting RegionServer {}", regionServerId);

        RegionServerMain regionServer = new RegionServerMain(regionServerId);

        try {
            regionServer.start();
            regionServer.runCommandLine();
        } catch (Exception e) {
            logger.error("RegionServer {} failed", regionServerId, e);
            System.exit(1);
        }
    }
}