package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;

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
}
