package com.minisql.master.balance;

import java.util.Objects;

/**
 * Configuration for Region migration operations.
 *
 * <p>This class defines timeouts, retry policies, and check intervals
 * for the migration state machine. Use the Builder pattern for custom
 * configurations or getDefault() for standard settings.
 *
 * @author MiniSQL Team
 */
public class MigrationConfig {

    private final long checkPeriodMs;
    private final int maxRetries;
    private final long prepareTimeoutMs;
    private final long syncTimeoutMs;
    private final long switchTimeoutMs;
    private final long rollbackTimeoutMs;

    /**
     * Private constructor - use Builder or getDefault().
     */
    private MigrationConfig(long checkPeriodMs,
                           int maxRetries,
                           long prepareTimeoutMs,
                           long syncTimeoutMs,
                           long switchTimeoutMs,
                           long rollbackTimeoutMs) {
        this.checkPeriodMs = checkPeriodMs;
        this.maxRetries = maxRetries;
        this.prepareTimeoutMs = prepareTimeoutMs;
        this.syncTimeoutMs = syncTimeoutMs;
        this.switchTimeoutMs = switchTimeoutMs;
        this.rollbackTimeoutMs = rollbackTimeoutMs;
    }

    /**
     * Returns default configuration with standard timeouts and retry settings.
     *
     * <p>Default values:
     * <ul>
     *   <li>Check period: 5000ms (5 seconds)</li>
     *   <li>Max retries: 3</li>
     *   <li>Prepare timeout: 30000ms (30 seconds)</li>
     *   <li>Sync timeout: 300000ms (5 minutes)</li>
     *   <li>Switch timeout: 30000ms (30 seconds)</li>
     *   <li>Rollback timeout: 60000ms (1 minute)</li>
     * </ul>
     *
     * @return default MigrationConfig instance
     */
    public static MigrationConfig getDefault() {
        return new MigrationConfig(
            5000L,      // checkPeriodMs
            3,          // maxRetries
            30000L,     // prepareTimeoutMs
            300000L,    // syncTimeoutMs
            30000L,     // switchTimeoutMs
            60000L      // rollbackTimeoutMs
        );
    }

    /**
     * Creates a new Builder for custom configuration.
     *
     * @return new Builder instance with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the period between migration state checks in milliseconds.
     *
     * @return check period in milliseconds
     */
    public long getCheckPeriodMs() {
        return checkPeriodMs;
    }

    /**
     * Returns the maximum number of retry attempts for failed operations.
     *
     * @return maximum retry count
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Returns the timeout for the PREPARE phase in milliseconds.
     *
     * @return prepare timeout in milliseconds
     */
    public long getPrepareTimeoutMs() {
        return prepareTimeoutMs;
    }

    /**
     * Returns the timeout for the SYNC phase in milliseconds.
     *
     * @return sync timeout in milliseconds
     */
    public long getSyncTimeoutMs() {
        return syncTimeoutMs;
    }

    /**
     * Returns the timeout for the SWITCH phase in milliseconds.
     *
     * @return switch timeout in milliseconds
     */
    public long getSwitchTimeoutMs() {
        return switchTimeoutMs;
    }

    /**
     * Returns the timeout for the ROLLBACK phase in milliseconds.
     *
     * @return rollback timeout in milliseconds
     */
    public long getRollbackTimeoutMs() {
        return rollbackTimeoutMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MigrationConfig that = (MigrationConfig) o;
        return checkPeriodMs == that.checkPeriodMs &&
               maxRetries == that.maxRetries &&
               prepareTimeoutMs == that.prepareTimeoutMs &&
               syncTimeoutMs == that.syncTimeoutMs &&
               switchTimeoutMs == that.switchTimeoutMs &&
               rollbackTimeoutMs == that.rollbackTimeoutMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkPeriodMs, maxRetries, prepareTimeoutMs,
                           syncTimeoutMs, switchTimeoutMs, rollbackTimeoutMs);
    }

    @Override
    public String toString() {
        return "MigrationConfig{" +
               "checkPeriodMs=" + checkPeriodMs +
               ", maxRetries=" + maxRetries +
               ", prepareTimeoutMs=" + prepareTimeoutMs +
               ", syncTimeoutMs=" + syncTimeoutMs +
               ", switchTimeoutMs=" + switchTimeoutMs +
               ", rollbackTimeoutMs=" + rollbackTimeoutMs +
               '}';
    }

    /**
     * Builder for creating custom MigrationConfig instances.
     *
     * <p>All fields are initialized with default values and can be
     * selectively overridden. Validation is performed on build().
     */
    public static class Builder {
        private long checkPeriodMs = 5000L;
        private int maxRetries = 3;
        private long prepareTimeoutMs = 30000L;
        private long syncTimeoutMs = 300000L;
        private long switchTimeoutMs = 30000L;
        private long rollbackTimeoutMs = 60000L;

        /**
         * Sets the check period in milliseconds.
         *
         * @param checkPeriodMs check period (must be positive)
         * @return this Builder
         */
        public Builder checkPeriodMs(long checkPeriodMs) {
            this.checkPeriodMs = checkPeriodMs;
            return this;
        }

        /**
         * Sets the maximum retry count.
         *
         * @param maxRetries maximum retries (must be non-negative)
         * @return this Builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the prepare phase timeout in milliseconds.
         *
         * @param prepareTimeoutMs prepare timeout (must be positive)
         * @return this Builder
         */
        public Builder prepareTimeoutMs(long prepareTimeoutMs) {
            this.prepareTimeoutMs = prepareTimeoutMs;
            return this;
        }

        /**
         * Sets the sync phase timeout in milliseconds.
         *
         * @param syncTimeoutMs sync timeout (must be positive)
         * @return this Builder
         */
        public Builder syncTimeoutMs(long syncTimeoutMs) {
            this.syncTimeoutMs = syncTimeoutMs;
            return this;
        }

        /**
         * Sets the switch phase timeout in milliseconds.
         *
         * @param switchTimeoutMs switch timeout (must be positive)
         * @return this Builder
         */
        public Builder switchTimeoutMs(long switchTimeoutMs) {
            this.switchTimeoutMs = switchTimeoutMs;
            return this;
        }

        /**
         * Sets the rollback phase timeout in milliseconds.
         *
         * @param rollbackTimeoutMs rollback timeout (must be positive)
         * @return this Builder
         */
        public Builder rollbackTimeoutMs(long rollbackTimeoutMs) {
            this.rollbackTimeoutMs = rollbackTimeoutMs;
            return this;
        }

        /**
         * Builds and validates the MigrationConfig.
         *
         * @return new MigrationConfig instance
         * @throws IllegalArgumentException if any value is invalid
         */
        public MigrationConfig build() {
            validate();
            return new MigrationConfig(
                checkPeriodMs,
                maxRetries,
                prepareTimeoutMs,
                syncTimeoutMs,
                switchTimeoutMs,
                rollbackTimeoutMs
            );
        }

        /**
         * Validates all configuration values.
         *
         * @throws IllegalArgumentException if any value is invalid
         */
        private void validate() {
            if (checkPeriodMs <= 0) {
                throw new IllegalArgumentException(
                    "checkPeriodMs must be positive, got: " + checkPeriodMs);
            }
            if (maxRetries < 0) {
                throw new IllegalArgumentException(
                    "maxRetries must be non-negative, got: " + maxRetries);
            }
            if (prepareTimeoutMs <= 0) {
                throw new IllegalArgumentException(
                    "prepareTimeoutMs must be positive, got: " + prepareTimeoutMs);
            }
            if (syncTimeoutMs <= 0) {
                throw new IllegalArgumentException(
                    "syncTimeoutMs must be positive, got: " + syncTimeoutMs);
            }
            if (switchTimeoutMs <= 0) {
                throw new IllegalArgumentException(
                    "switchTimeoutMs must be positive, got: " + switchTimeoutMs);
            }
            if (rollbackTimeoutMs <= 0) {
                throw new IllegalArgumentException(
                    "rollbackTimeoutMs must be positive, got: " + rollbackTimeoutMs);
            }
        }
    }
}
