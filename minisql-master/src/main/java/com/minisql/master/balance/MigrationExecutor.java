package com.minisql.master.balance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of migration executor that simulates RegionServer gRPC calls.
 */
public class MigrationExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MigrationExecutor.class);
    private static final long SYNC_DURATION_MS = 500;
    private static final long SYNC_RATE_BYTES_PER_MS = 100;

    private final double failureRate;
    private final Random random = new Random();
    private final Map<String, Long> syncStartTimes = new ConcurrentHashMap<>();

    public MigrationExecutor() {
        this(0.0);
    }

    public MigrationExecutor(double failureRate) {
        this.failureRate = failureRate;
    }

    public boolean prepareSource(String regionId, String serverId) throws MigrationException {
        logger.info("Preparing source region {} on server {}", regionId, serverId);
        simulateDelay();
        checkFailure("prepareSource");
        return true;
    }

    public boolean prepareTarget(String regionId, String serverId) throws MigrationException {
        logger.info("Preparing target region {} on server {}", regionId, serverId);
        simulateDelay();
        checkFailure("prepareTarget");
        return true;
    }

    public boolean startSync(String regionId, String sourceServerId, String targetServerId) throws MigrationException {
        logger.info("Starting sync for region {} from {} to {}", regionId, sourceServerId, targetServerId);
        simulateDelay();
        checkFailure("startSync");
        syncStartTimes.put(regionId, System.currentTimeMillis());
        return true;
    }

    public SyncProgress getSyncProgress(String regionId) throws MigrationException {
        logger.debug("Getting sync progress for region {}", regionId);
        checkFailure("getSyncProgress");

        Long startTime = syncStartTimes.get(regionId);
        if (startTime == null) {
            throw new MigrationException("Sync not started for region: " + regionId);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        long syncedBytes = Math.min(elapsed * SYNC_RATE_BYTES_PER_MS, SYNC_DURATION_MS * SYNC_RATE_BYTES_PER_MS);
        long totalBytes = SYNC_DURATION_MS * SYNC_RATE_BYTES_PER_MS;
        boolean completed = elapsed >= SYNC_DURATION_MS;

        return new SyncProgress(totalBytes, syncedBytes, completed);
    }

    public boolean activateRegion(String regionId, String serverId) throws MigrationException {
        logger.info("Activating region {} on server {}", regionId, serverId);
        simulateDelay();
        checkFailure("activateRegion");
        return true;
    }

    public boolean deactivateRegion(String regionId, String serverId) throws MigrationException {
        logger.info("Deactivating region {} on server {}", regionId, serverId);
        simulateDelay();
        checkFailure("deactivateRegion");
        return true;
    }

    public boolean cleanupTarget(String regionId, String serverId) throws MigrationException {
        logger.info("Cleaning up target region {} on server {}", regionId, serverId);
        simulateDelay();
        checkFailure("cleanupTarget");
        syncStartTimes.remove(regionId);
        return true;
    }

    public boolean restoreSource(String regionId, String serverId) throws MigrationException {
        logger.info("Restoring source region {} on server {}", regionId, serverId);
        simulateDelay();
        checkFailure("restoreSource");
        syncStartTimes.remove(regionId);
        return true;
    }

    public void cleanupSyncState(String regionId) {
        syncStartTimes.remove(regionId);
    }

    private void simulateDelay() {
        try {
            Thread.sleep(100 + random.nextInt(400));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkFailure(String operation) throws MigrationException {
        if (random.nextDouble() < failureRate) {
            throw new MigrationException("Simulated failure in " + operation, true);
        }
    }
}
