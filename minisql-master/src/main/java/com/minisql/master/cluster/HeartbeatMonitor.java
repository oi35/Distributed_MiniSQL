package com.minisql.master.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 心跳监控器
 *
 * 定期检查RegionServer心跳，检测超时并触发故障处理
 */
public class HeartbeatMonitor {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final ClusterManager clusterManager;
    private final ScheduledExecutorService scheduler;
    private final long checkIntervalMs;
    private volatile boolean running;

    /**
     * 故障处理器接口
     */
    public interface FailureHandler {
        /**
         * 处理服务器故障
         *
         * @param serverId 故障的服务器ID
         */
        void handleServerFailure(String serverId);
    }

    private FailureHandler failureHandler;

    public HeartbeatMonitor(ClusterManager clusterManager, long checkIntervalMs) {
        this.clusterManager = clusterManager;
        this.checkIntervalMs = checkIntervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "HeartbeatMonitor");
            thread.setDaemon(true);
            return thread;
        });
        this.running = false;
    }

    /**
     * 设置故障处理器
     */
    public void setFailureHandler(FailureHandler handler) {
        this.failureHandler = handler;
    }

    /**
     * 启动监控
     */
    public void start() {
        if (running) {
            logger.warn("HeartbeatMonitor already running");
            return;
        }

        running = true;
        scheduler.scheduleAtFixedRate(
                this::checkHeartbeats,
                checkIntervalMs,
                checkIntervalMs,
                TimeUnit.MILLISECONDS
        );

        logger.info("HeartbeatMonitor started, check interval: {}ms", checkIntervalMs);
    }

    /**
     * 停止监控
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            logger.info("HeartbeatMonitor stopped");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            logger.error("HeartbeatMonitor shutdown interrupted", e);
        }
    }

    /**
     * 检查心跳
     */
    private void checkHeartbeats() {
        try {
            List<String> timeoutServers = clusterManager.checkTimeoutServers();

            if (!timeoutServers.isEmpty()) {
                logger.warn("Detected {} timeout servers: {}",
                           timeoutServers.size(), timeoutServers);

                // 触发故障处理
                if (failureHandler != null) {
                    for (String serverId : timeoutServers) {
                        try {
                            failureHandler.handleServerFailure(serverId);
                        } catch (Exception e) {
                            logger.error("Error handling failure for server: {}", serverId, e);
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error checking heartbeats", e);
        }
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
}
