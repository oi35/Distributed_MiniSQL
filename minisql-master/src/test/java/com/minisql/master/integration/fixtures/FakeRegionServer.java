package com.minisql.master.integration.fixtures;

import com.minisql.common.proto.ServerMetrics;
import com.minisql.master.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FakeRegionServer {

    public enum FailureMode {
        NONE,
        SLOW,
        FAIL_PREPARE,
        FAIL_SYNC,
        DISCONNECT
    }

    private final String serverId;
    private final Map<String, Long> regions;
    private ManagedChannel channel;
    private MasterServiceGrpc.MasterServiceBlockingStub stub;
    private FailureMode failureMode;

    public FakeRegionServer(String serverId) {
        this.serverId = serverId;
        this.regions = new ConcurrentHashMap<>();
        this.failureMode = FailureMode.NONE;
    }

    public void start(int port) {
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        stub = MasterServiceGrpc.newBlockingStub(channel);
    }

    public void stop() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                channel.shutdownNow();
            }
        }
    }

    public void addRegion(String regionId, long sizeBytes) {
        regions.put(regionId, sizeBytes);
    }

    public void setFailureMode(FailureMode mode) {
        this.failureMode = mode;
    }

    public HeartbeatResponse heartbeat() {
        if (failureMode == FailureMode.DISCONNECT) {
            throw new RuntimeException("Simulated disconnect");
        }

        if (failureMode == FailureMode.SLOW) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        HeartbeatRequest request = HeartbeatRequest.newBuilder()
                .setServerId(serverId)
                .setTimestamp(System.currentTimeMillis())
                .addAllRegionIds(regions.keySet())
                .setMetrics(ServerMetrics.newBuilder()
                        .setCpuUsage(50.0)
                        .setMemoryUsage(50.0)
                        .setDiskUsedBytes(2048L * 1024 * 1024)
                        .setDiskTotalBytes(10240L * 1024 * 1024)
                        .build())
                .build();

        return stub.sendHeartbeat(request);
    }
}
