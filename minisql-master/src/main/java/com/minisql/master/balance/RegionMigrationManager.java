package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
            .filter(task -> !task.getState().isTerminal() ||
                   (task.getState() == MigrationState.FAILED && shouldRetry(task)))
            .collect(Collectors.toList());
    }

    public String submitMigration(String regionId, String sourceServerId, String targetServerId) {
        if (regionId == null || sourceServerId == null || targetServerId == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        if (sourceServerId.equals(targetServerId)) {
            throw new IllegalArgumentException("Source and target server cannot be the same");
        }
        String migrationId = generateMigrationId();
        MigrationTask task = new MigrationTask(migrationId, regionId, sourceServerId, targetServerId);
        migrations.put(migrationId, task);
        logger.info("Migration submitted: {}", task);
        return migrationId;
    }

    public MigrationTask getTask(String migrationId) {
        return migrations.get(migrationId);
    }

    private String generateMigrationId() {
        return "migration-" + System.currentTimeMillis() + "-" +
               (int)(Math.random() * 10000);
    }

    private void processTasks() {
        for (MigrationTask task : getActiveMigrations()) {
            try {
                advanceTask(task);
            } catch (Exception e) {
                logger.error("Error processing task {}: {}", task.getMigrationId(), e.getMessage(), e);
            }
        }
    }

    private void advanceTask(MigrationTask task) {
        MigrationState currentState = task.getState();

        if (currentState == MigrationState.PENDING) {
            task.setState(MigrationState.MIGRATING_PREPARE);
            task.setStartTime(System.currentTimeMillis());
            logger.info("Task {} transitioned to MIGRATING_PREPARE", task.getMigrationId());
            return;
        }

        if (currentState == MigrationState.FAILED) {
            if (shouldRetry(task)) {
                Long retryTime = (Long) task.getMetadata("retryTime");
                if (retryTime != null && System.currentTimeMillis() >= retryTime) {
                    task.setState(MigrationState.PENDING);
                    logger.info("Retrying task {}, attempt {}", task.getMigrationId(), task.getRetryCount() + 1);
                }
            }
            return;
        }

        MigrationStateHandler handler = handlers.get(currentState);
        if (handler == null) {
            return;
        }

        try {
            MigrationState nextState = handler.handle(task, executor);
            if (nextState != null && nextState != currentState) {
                task.setState(nextState);
                logger.info("Task {} transitioned from {} to {}",
                    task.getMigrationId(), currentState, nextState);
            }
        } catch (MigrationException e) {
            logger.error("Task {} failed in state {}: {}",
                task.getMigrationId(), currentState, e.getMessage());
            handleFailure(task, e.getMessage());
        }
    }

    private void handleFailure(MigrationTask task, String errorMessage) {
        task.setErrorMessage(errorMessage);
        if (shouldRetry(task)) {
            task.incrementRetry();
            int retryCount = task.getRetryCount();
            long retryDelay = getRetryDelay(retryCount);
            task.setMetadata("retryTime", System.currentTimeMillis() + retryDelay);
            task.setState(MigrationState.FAILED);
            logger.info("Task {} will retry in {}ms (attempt {})",
                task.getMigrationId(), retryDelay, retryCount);
        } else {
            task.setState(MigrationState.FAILED);
            task.setEndTime(System.currentTimeMillis());
            logger.error("Task {} failed permanently after {} retries",
                task.getMigrationId(), task.getRetryCount());
        }
    }

    private boolean shouldRetry(MigrationTask task) {
        return task.getRetryCount() < config.getMaxRetries();
    }

    private long getRetryDelay(int retryCount) {
        return 60000 * (1L << retryCount);
    }

    public List<MigrationTask> getAllTasks() {
        return new ArrayList<>(migrations.values());
    }

    public List<MigrationTask> getTasksByState(MigrationState state) {
        return migrations.values().stream()
            .filter(task -> task.getState() == state)
            .collect(Collectors.toList());
    }

    public List<MigrationTask> getTasksByServer(String serverId) {
        return migrations.values().stream()
            .filter(task -> task.getSourceServerId().equals(serverId) ||
                           task.getTargetServerId().equals(serverId))
            .collect(Collectors.toList());
    }

    public boolean cancelMigration(String migrationId) {
        MigrationTask task = migrations.get(migrationId);
        if (task == null || task.getState().isTerminal()) {
            return false;
        }
        task.setState(MigrationState.CANCELLED);
        task.setEndTime(System.currentTimeMillis());
        return true;
    }

    public boolean retryMigration(String migrationId) {
        MigrationTask task = migrations.get(migrationId);
        if (task == null || task.getState() != MigrationState.FAILED) {
            return false;
        }
        task.setState(MigrationState.PENDING);
        task.setErrorMessage(null);
        task.removeMetadata("retryTime");
        return true;
    }

    public MigrationStatistics getStatistics() {
        int total = migrations.size();
        int completed = 0;
        int failed = 0;
        int cancelled = 0;
        int active = 0;
        long totalDuration = 0;
        int completedCount = 0;

        for (MigrationTask task : migrations.values()) {
            MigrationState state = task.getState();
            if (state == MigrationState.COMPLETED) {
                completed++;
                if (task.getStartTime() > 0 && task.getEndTime() > 0) {
                    totalDuration += task.getEndTime() - task.getStartTime();
                    completedCount++;
                }
            } else if (state == MigrationState.FAILED) {
                failed++;
            } else if (state == MigrationState.CANCELLED) {
                cancelled++;
            } else {
                active++;
            }
        }

        long avgDuration = completedCount > 0 ? totalDuration / completedCount : 0;
        return new MigrationStatistics(total, completed, failed, cancelled, active, avgDuration);
    }
}
