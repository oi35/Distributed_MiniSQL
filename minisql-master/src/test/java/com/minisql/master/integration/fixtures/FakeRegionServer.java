package com.minisql.master.integration.fixtures;

import com.minisql.common.proto.ServerMetrics;
import com.minisql.master.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Map;
import java.util.concurrent.*;

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
    private ScheduledExecutorService heartbeatScheduler;
    private volatile boolean autoHeartbeatEnabled;

    public FakeRegionServer(String serverId) {
        this.serverId = serverId;
        this.regions = new ConcurrentHashMap<>();
        this.failureMode = FailureMode.NONE;
        this.autoHeartbeatEnabled = false;
    }

    public void start(int port) {
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        stub = MasterServiceGrpc.newBlockingStub(channel);
    }

    public RegisterRegionServerResponse register(String host, int port) {
        RegisterRegionServerRequest request = RegisterRegionServerRequest.newBuilder()
                .setServerId(serverId)
                .setHost(host)
                .setPort(port)
                .build();

        return stub.registerRegionServer(request);
    }

    /**
     * Start automatic heartbeat sending (every 2 seconds)
     */
    public void startAutoHeartbeat() {
        if (autoHeartbeatEnabled) {
            return;
        }
        autoHeartbeatEnabled = true;
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "FakeRS-" + serverId + "-Heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (autoHeartbeatEnabled && failureMode != FailureMode.DISCONNECT) {
                    heartbeat();
                }
            } catch (Exception e) {
                // Ignore heartbeat errors
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    /**
     * Stop automatic heartbeat sending
     */
    public void stopAutoHeartbeat() {
        autoHeartbeatEnabled = false;
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
    }

    public void stop() {
        stopAutoHeartbeat();
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
