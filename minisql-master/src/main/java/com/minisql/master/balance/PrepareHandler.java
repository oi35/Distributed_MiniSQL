package com.minisql.master.balance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for MIGRATING_PREPARE state.
 * Notifies source and target RegionServers to prepare for migration.
 */
public class PrepareHandler implements MigrationStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(PrepareHandler.class);
    private final MigrationConfig config;

    public PrepareHandler(MigrationConfig config) {
        this.config = config;
    }

    @Override
    public MigrationState supportedState() {
        return MigrationState.MIGRATING_PREPARE;
    }

    @Override
    public MigrationState handle(MigrationTask task, MigrationExecutor executor)
            throws MigrationException {
        if (isTimeout(task)) {
            String error = "Prepare phase timeout after " + config.getPrepareTimeoutMs() + "ms";
            task.setErrorMessage(error);
            throw new MigrationException(error);
        }

        if (task.getMetadata("prepareStartTime") == null) {
            task.setMetadata("prepareStartTime", System.currentTimeMillis());
        }

        try {
            executor.prepareSource(task.getRegionId(), task.getSourceServerId());
            executor.prepareTarget(task.getRegionId(), task.getTargetServerId());
            logger.info("Prepare phase completed for task {}", task.getMigrationId());
            return MigrationState.MIGRATING_SYNC;
        } catch (MigrationException e) {
            task.setErrorMessage(e.getMessage());
            throw e;
        }
    }

    private boolean isTimeout(MigrationTask task) {
        Long startTime = (Long) task.getMetadata("prepareStartTime");
        if (startTime == null) {
            startTime = task.getStartTime();
        }
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > config.getPrepareTimeoutMs();
    }
}
