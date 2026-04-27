package com.minisql.master.balance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for MIGRATING_SYNC state.
 * Starts data synchronization and monitors progress until completion.
 */
public class SyncHandler implements MigrationStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(SyncHandler.class);
    private static final String SYNC_STARTED_KEY = "syncStarted";
    private final MigrationConfig config;

    public SyncHandler(MigrationConfig config) {
        this.config = config;
    }

    @Override
    public MigrationState supportedState() {
        return MigrationState.MIGRATING_SYNC;
    }

    @Override
    public MigrationState handle(MigrationTask task, MigrationExecutor executor)
            throws MigrationException {
        if (isTimeout(task)) {
            String error = "Sync phase timeout after " + config.getSyncTimeoutMs() + "ms";
            task.setErrorMessage(error);
            throw new MigrationException(error);
        }

        if (task.getMetadata(SYNC_STARTED_KEY) == null) {
            task.setMetadata("syncStartTime", System.currentTimeMillis());
            executor.startSync(task.getRegionId(), task.getSourceServerId(), task.getTargetServerId());
            task.setMetadata(SYNC_STARTED_KEY, true);
            logger.info("Started sync for task {}", task.getMigrationId());
            return null;
        }

        SyncProgress progress = executor.getSyncProgress(task.getRegionId());
        if (progress.isCompleted()) {
            logger.info("Sync phase completed for task {}", task.getMigrationId());
            return MigrationState.MIGRATING_SWITCH;
        }

        return null;
    }

    private boolean isTimeout(MigrationTask task) {
        Long startTime = (Long) task.getMetadata("syncStartTime");
        if (startTime == null) {
            startTime = task.getStartTime();
        }
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > config.getSyncTimeoutMs();
    }
}
