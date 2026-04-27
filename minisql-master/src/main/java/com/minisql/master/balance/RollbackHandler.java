package com.minisql.master.balance;

import com.minisql.master.metadata.MetadataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for ROLLING_BACK state.
 * Rolls back migration by restoring source and cleaning up target.
 */
public class RollbackHandler implements MigrationStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(RollbackHandler.class);
    private static final String ROUTE_UPDATED_KEY = "routeUpdated";
    private final MigrationConfig config;
    private final MetadataManager metadataManager;

    public RollbackHandler(MigrationConfig config, MetadataManager metadataManager) {
        this.config = config;
        this.metadataManager = metadataManager;
    }

    @Override
    public MigrationState supportedState() {
        return MigrationState.ROLLING_BACK;
    }

    @Override
    public MigrationState handle(MigrationTask task, MigrationExecutor executor)
            throws MigrationException {
        try {
            Boolean routeUpdated = (Boolean) task.getMetadata(ROUTE_UPDATED_KEY);
            if (routeUpdated != null && routeUpdated) {
                metadataManager.updateRegionLocation(task.getRegionId(), task.getSourceServerId());
            }

            executor.cleanupTarget(task.getRegionId(), task.getTargetServerId());
            executor.restoreSource(task.getRegionId(), task.getSourceServerId());

            logger.info("Rollback completed for task {}", task.getMigrationId());
            return MigrationState.FAILED;
        } catch (Exception e) {
            task.setErrorMessage("Rollback error: " + e.getMessage());
            logger.error("Rollback error for task {}: {}", task.getMigrationId(), e.getMessage());
            return MigrationState.FAILED;
        }
    }
}
