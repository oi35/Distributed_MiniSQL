package com.minisql.admin;

import com.minisql.common.proto.RouteEntry;
import com.minisql.common.proto.ServerState;
import com.minisql.master.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AdminGrpcClient {

    private final ManagedChannel channel;
    private final AdminServiceGrpc.AdminServiceBlockingStub adminStub;
    private final ClientMasterServiceGrpc.ClientMasterServiceBlockingStub clientStub;

    public AdminGrpcClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.adminStub = AdminServiceGrpc.newBlockingStub(channel);
        this.clientStub = ClientMasterServiceGrpc.newBlockingStub(channel);
    }

    public void printClusterHealth() {
        GetClusterHealthResponse response = clientStub.getClusterHealth(
                GetClusterHealthRequest.newBuilder().build());

        String statusStr;
        switch (response.getStatus()) {
            case HEALTHY:
                statusStr = "HEALTHY";
                break;
            case DEGRADED:
                statusStr = "DEGRADED";
                break;
            case CRITICAL:
                statusStr = "CRITICAL";
                break;
            default:
                statusStr = "UNKNOWN";
        }

        System.out.println("Cluster Health: " + statusStr);
        System.out.println("  Total Servers: " + response.getTotalServers());
        System.out.println("  Online Servers: " + response.getOnlineServers());
        System.out.println("  Total Regions: " + response.getTotalRegions());
        System.out.println("  Online Regions: " + response.getOnlineRegions());

        if (response.getIssuesCount() > 0) {
            System.out.println("  Issues:");
            for (String issue : response.getIssuesList()) {
                System.out.println("    - " + issue);
            }
        }
    }

    public void printClusterStats() {
        GetClusterStatsResponse response = clientStub.getClusterStats(
                GetClusterStatsRequest.newBuilder().build());

        System.out.println("Cluster Statistics:");
        System.out.println("  Total Tables: " + response.getTotalTables());
        System.out.println("  Total Regions: " + response.getTotalRegions());
        System.out.println("  Total Data Size: " + formatBytes(response.getTotalDataSizeBytes()));
        System.out.println("  Total Rows: " + response.getTotalRowCount());
        System.out.println("  Avg Region Size: " + String.format("%.2f", response.getAverageRegionSizeMb()) + " MB");
        System.out.println("  Regions Splitting: " + response.getRegionsSplitting());
        System.out.println("  Regions Migrating: " + response.getRegionsMigrating());
        System.out.println("  Total QPS: " + response.getTotalQps());
    }

    public void printServerList() {
        ListServersResponse response = adminStub.listServers(
                ListServersRequest.newBuilder().build());

        System.out.println("RegionServer List (" + response.getOnlineCount() + "/" + response.getTotalCount() + " online):");
        System.out.println();

        for (ServerDetail server : response.getServersList()) {
            String stateStr;
            switch (server.getState()) {
                case SERVER_ONLINE:
                    stateStr = "ONLINE";
                    break;
                case SERVER_OFFLINE:
                    stateStr = "OFFLINE";
                    break;
                case SERVER_DEAD:
                    stateStr = "DEAD";
                    break;
                default:
                    stateStr = "UNKNOWN";
            }

            System.out.println("  Server: " + server.getServerId());
            System.out.println("    Address: " + server.getAddress());
            System.out.println("    State: " + stateStr);
            System.out.println("    Load Score: " + String.format("%.2f", server.getLoadScore()));
            System.out.println("    Regions: " + server.getRegionCount());
            System.out.println("    Data Size: " + formatBytes(server.getTotalSizeBytes()));
            System.out.println("    Uptime: " + formatUptime(server.getUptimeMs()));
            System.out.println("    Last Heartbeat: " + server.getLastHeartbeatTime());
            System.out.println();
        }
    }

    public void triggerBalance() {
        TriggerBalanceResponse response = adminStub.triggerBalance(
                TriggerBalanceRequest.newBuilder().build());

        System.out.println("Balance Result: " + (response.getSuccess() ? "SUCCESS" : "FAILED"));
        System.out.println("  Message: " + response.getMessage());
        System.out.println("  Plans Generated: " + response.getPlansGenerated());
    }

    public void printTableList() {
        ListTablesResponse response = clientStub.listTables(
                ListTablesRequest.newBuilder().build());

        if (response.getTableNamesCount() == 0) {
            System.out.println("No tables found.");
            return;
        }

        System.out.println("Tables (" + response.getTableNamesCount() + "):");
        for (String tableName : response.getTableNamesList()) {
            System.out.println("  - " + tableName);
        }
    }

    public void printTableSchema(String tableName) {
        GetTableSchemaResponse response = clientStub.getTableSchema(
                GetTableSchemaRequest.newBuilder()
                        .setTableName(tableName)
                        .build());

        if (!response.getSuccess()) {
            System.out.println("Error: " + response.getErrorMessage());
            return;
        }

        var schema = response.getSchema();
        System.out.println("Table: " + schema.getTableName());
        System.out.println("Primary Key: " + schema.getPrimaryKey());
        System.out.println("Version: " + schema.getVersion());
        System.out.println("Columns:");
        System.out.println("  +-----------------+------------------+----------+-------+");
        System.out.println("  | Name            | Type             | Nullable | Default |");
        System.out.println("  +-----------------+------------------+----------+-------+");

        for (var column : schema.getColumnsList()) {
            System.out.printf("  | %-15s | %-16s | %-8s | %-5s |%n",
                    column.getName(),
                    column.getType(),
                    column.getNullable() ? "YES" : "NO",
                    column.getDefaultValue().isEmpty() ? "" : column.getDefaultValue());
        }
        System.out.println("  +-----------------+------------------+----------+-------+");
    }

    public void printTableRoute(String tableName) {
        GetRouteTableResponse response = clientStub.getRouteTable(
                GetRouteTableRequest.newBuilder()
                        .setTableName(tableName)
                        .setCachedVersion(0)
                        .build());

        if (!response.getSuccess()) {
            System.out.println("Error: " + response.getErrorMessage());
            return;
        }

        var routeTable = response.getRouteTable();
        System.out.println("Route Table: " + routeTable.getTableName());
        System.out.println("Version: " + routeTable.getVersion());

        if (routeTable.getRoutesCount() == 0) {
            System.out.println("  No regions found.");
            return;
        }

        System.out.println("Regions (" + routeTable.getRoutesCount() + "):");
        for (RouteEntry route : routeTable.getRoutesList()) {
            System.out.println("  Region: " + route.getRegionId());
            System.out.println("    Range: [" + route.getStartKey().toStringUtf8()
                    + ", " + route.getEndKey().toStringUtf8() + ")");
            System.out.println("    Primary: " + route.getPrimaryServer());
            if (route.getReplicaServersCount() > 0) {
                System.out.println("    Replicas: " + String.join(", ", route.getReplicaServersList()));
            }
            if (route.getReplicaAddressesCount() > 0) {
                System.out.println("    Replica Addresses: " + String.join(", ", route.getReplicaAddressesList()));
            }
            System.out.println();
        }
    }

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
