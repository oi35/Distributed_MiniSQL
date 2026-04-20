package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 负载均衡器
 */
public class LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancer.class);

    private final ClusterManager clusterManager;
    private final MetadataManager metadataManager;
    private final RegionMigrationManager migrationManager;
    private final MasterElection masterElection;
    private final LoadBalancerConfig config;

    private volatile boolean running;
    private volatile long lastBalanceTime;
    private ScheduledExecutorService scheduler;

    public LoadBalancer(ClusterManager clusterManager,
                       MetadataManager metadataManager,
                       RegionMigrationManager migrationManager,
                       MasterElection masterElection,
                       LoadBalancerConfig config) {
        this.clusterManager = clusterManager;
        this.metadataManager = metadataManager;
        this.migrationManager = migrationManager;
        this.masterElection = masterElection;
        this.config = config;
        this.running = false;
        this.lastBalanceTime = 0;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) {
            logger.warn("LoadBalancer already running");
            return;
        }

        if (!masterElection.isLeader()) {
            logger.warn("Not the leader, LoadBalancer will not start");
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "load-balancer");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
            this::checkAndBalance,
            config.getCheckPeriodMs(),
            config.getCheckPeriodMs(),
            TimeUnit.MILLISECONDS
        );

        logger.info("LoadBalancer started, check period: {}ms", config.getCheckPeriodMs());
    }

    public void stop() {
        if (!running) {
            logger.warn("LoadBalancer not running");
            return;
        }

        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("LoadBalancer stopped");
    }

    private void checkAndBalance() {
        // Placeholder - will implement in next task
    }
}
