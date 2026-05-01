package com.minisql.master.balance;

import com.minisql.master.metadata.MetadataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for MIGRATING_SWITCH state.
 * Updates route table and switches traffic from source to target.
 */
public class SwitchHandler implements MigrationStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(SwitchHandler.class);
    private static final String ROUTE_UPDATED_KEY = "routeUpdated";
    private final MigrationConfig config;
    private final MetadataManager metadataManager;

    public SwitchHandler(MigrationConfig config, MetadataManager metadataManager) {
        this.config = config;
        this.metadataManager = metadataManager;
    }

    @Override
    public MigrationState supportedState() {
        return MigrationState.MIGRATING_SWITCH;
    }

    @Override
    public MigrationState handle(MigrationTask task, MigrationExecutor executor)
            throws MigrationException {
        if (task.getMetadata("switchStartTime") == null) {
            task.setMetadata("switchStartTime", System.currentTimeMillis());
        }

        if (isTimeout(task)) {
            String error = "Switch phase timeout after " + config.getSwitchTimeoutMs() + "ms";
            task.setErrorMessage(error);
            throw new MigrationException(error);
        }

        try {
            metadataManager.updateRegionLocation(task.getRegionId(), task.getTargetServerId());
            task.setMetadata(ROUTE_UPDATED_KEY, true);

            executor.activateRegion(task.getRegionId(), task.getTargetServerId());
            executor.deactivateRegion(task.getRegionId(), task.getSourceServerId());

            executor.cleanupSyncState(task.getRegionId());

            logger.info("Switch phase completed for task {}", task.getMigrationId());
            return MigrationState.COMPLETED;
        } catch (MigrationException e) {
            task.setErrorMessage(e.getMessage());
            throw e;
        }
    }

    private boolean isTimeout(MigrationTask task) {
        Long startTime = (Long) task.getMetadata("switchStartTime");
        if (startTime == null) {
            startTime = task.getStartTime();
        }
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > config.getSwitchTimeoutMs();
    }
}
