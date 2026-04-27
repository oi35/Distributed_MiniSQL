package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Region迁移管理器
 */
public class RegionMigrationManager {

    private static final Logger logger = LoggerFactory.getLogger(RegionMigrationManager.class);

    private final ClusterManager clusterManager;
    private final MetadataManager metadataManager;
    private final MigrationConfig config;
    private final MigrationExecutor executor;
    private final Map<String, MigrationTask> migrations = new ConcurrentHashMap<>();
    private final Map<MigrationState, MigrationStateHandler> handlers = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public RegionMigrationManager(ClusterManager clusterManager,
                                 MetadataManager metadataManager,
                                 MigrationConfig config,
                                 MigrationExecutor executor) {
        this.clusterManager = clusterManager;
        this.metadataManager = metadataManager;
        this.config = config;
        this.executor = executor;
        initHandlers();
    }

    private void initHandlers() {
        handlers.put(MigrationState.MIGRATING_PREPARE, new PrepareHandler(config));
        handlers.put(MigrationState.MIGRATING_SYNC, new SyncHandler(config));
        handlers.put(MigrationState.MIGRATING_SWITCH, new SwitchHandler(config, metadataManager));
        handlers.put(MigrationState.ROLLING_BACK, new RollbackHandler(config, metadataManager));
    }

    public void start() {
        if (running) {
            logger.warn("RegionMigrationManager already running");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            this::processTasks,
            config.getCheckPeriodMs(),
            config.getCheckPeriodMs(),
            TimeUnit.MILLISECONDS
        );
        running = true;
        logger.info("RegionMigrationManager started");
    }

    public void stop() {
        if (!running) {
            logger.warn("RegionMigrationManager not running");
            return;
        }
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("RegionMigrationManager stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public List<MigrationTask> getActiveMigrations() {
        return migrations.values().stream()
            .filter(task -> !task.getState().isTerminal())
            .collect(Collectors.toList());
    }

    private void processTasks() {
        // Empty for now, will implement in Task 4.3
    }
}
