package com.minisql.admin;

public class MiniSqlAdmin {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8000;

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printHelp();
            System.exit(0);
        }

        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        int argIndex = 0;
        while (argIndex < args.length && args[argIndex].startsWith("--")) {
            switch (args[argIndex]) {
                case "--host":
                    if (++argIndex < args.length) host = args[argIndex];
                    break;
                case "--port":
                    if (++argIndex < args.length) port = Integer.parseInt(args[argIndex]);
                    break;
                default:
                    System.err.println("Unknown option: " + args[argIndex]);
                    System.exit(1);
            }
            argIndex++;
        }

        if (argIndex >= args.length) {
            printHelp();
            System.exit(1);
        }

        AdminGrpcClient client = null;
        try {
            client = new AdminGrpcClient(host, port);

            String command = args[argIndex];
            String[] commandArgs = new String[args.length - argIndex - 1];
            System.arraycopy(args, argIndex + 1, commandArgs, 0, commandArgs.length);

            switch (command) {
                case "cluster":
                    handleClusterCommand(client, commandArgs);
                    break;
                case "table":
                    handleTableCommand(client, commandArgs);
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printHelp();
                    System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } finally {
            if (client != null) {
                try { client.shutdown(); } catch (Exception ignored) {}
            }
        }
    }

    private static void handleClusterCommand(AdminGrpcClient client, String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: minisql-admin cluster <status|stats|nodes|balance>");
            System.exit(1);
        }

        switch (args[0]) {
            case "status":
                client.printClusterHealth();
                break;
            case "stats":
                client.printClusterStats();
                break;
            case "nodes":
                client.printServerList();
                break;
            case "balance":
                client.triggerBalance();
                break;
            default:
                System.err.println("Unknown cluster command: " + args[0]);
                System.out.println("Available: status, stats, nodes, balance");
                System.exit(1);
        }
    }

    private static void handleTableCommand(AdminGrpcClient client, String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: minisql-admin table <list|describe|route> [tableName]");
            System.exit(1);
        }

        switch (args[0]) {
            case "list":
                client.printTableList();
                break;
            case "describe":
                if (args.length < 2) {
                    System.err.println("Usage: minisql-admin table describe <tableName>");
                    System.exit(1);
                }
                client.printTableSchema(args[1]);
                break;
            case "route":
                if (args.length < 2) {
                    System.err.println("Usage: minisql-admin table route <tableName>");
                    System.exit(1);
                }
                client.printTableRoute(args[1]);
                break;
            default:
                System.err.println("Unknown table command: " + args[0]);
                System.out.println("Available: list, describe, route");
                System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("MiniSQL Admin CLI - 分布式MiniSQL系统管理工具");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  minisql-admin [--host <host>] [--port <port>] <command> [args]");
        System.out.println();
        System.out.println("全局选项:");
        System.out.println("  --host <host>      Master服务器地址 (默认: localhost)");
        System.out.println("  --port <port>      Master服务器端口 (默认: 8000)");
        System.out.println("  --help, -h         显示帮助信息");
        System.out.println();
        System.out.println("集群管理命令:");
        System.out.println("  cluster status     查看集群健康状态");
        System.out.println("  cluster stats      查看集群统计信息");
        System.out.println("  cluster nodes      列出所有RegionServer节点");
        System.out.println("  cluster balance    触发手动负载均衡");
        System.out.println();
        System.out.println("表管理命令:");
        System.out.println("  table list         列出所有表");
        System.out.println("  table describe <t> 查看表结构");
        System.out.println("  table route <t>    查看表路由信息");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  minisql-admin cluster status");
        System.out.println("  minisql-admin --host 192.168.1.10 --port 8000 cluster nodes");
        System.out.println("  minisql-admin table list");
        System.out.println("  minisql-admin table describe users");
    }
}
