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
        try {
            metadataManager.updateRegionLocation(task.getRegionId(), task.getTargetServerId());
            task.setMetadata(ROUTE_UPDATED_KEY, true);

            executor.activateRegion(task.getRegionId(), task.getTargetServerId());
            executor.deactivateRegion(task.getRegionId(), task.getSourceServerId());

            logger.info("Switch phase completed for task {}", task.getMigrationId());
            return MigrationState.COMPLETED;
        } catch (MigrationException e) {
            task.setErrorMessage(e.getMessage());
            throw e;
        }
    }
}
