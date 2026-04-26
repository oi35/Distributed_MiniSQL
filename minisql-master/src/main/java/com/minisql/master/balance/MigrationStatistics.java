package com.minisql.master.balance;

import java.util.Objects;

/**
 * Immutable data class holding statistics about migration tasks.
 *
 * <p>This class tracks various metrics about region migration operations including:
 * <ul>
 *   <li>Total number of submitted migration tasks</li>
 *   <li>Number of completed, failed, cancelled, and active tasks</li>
 *   <li>Average duration of completed migrations</li>
 *   <li>Calculated success rate</li>
 * </ul>
 *
 * <p>All fields are immutable and set via constructor. The success rate is calculated
 * as the ratio of completed tasks to total finished tasks (completed + failed).
 *
 * @author MiniSQL Team
 * @since 1.0
 */
public class MigrationStatistics {

    private final int totalSubmitted;
    private final int completed;
    private final int failed;
    private final int cancelled;
    private final int active;
    private final long avgDurationMs;

    /**
     * Constructs a new MigrationStatistics instance.
     *
     * @param totalSubmitted total number of migration tasks submitted
     * @param completed number of successfully completed tasks
     * @param failed number of failed tasks
     * @param cancelled number of cancelled tasks
     * @param active number of currently active tasks
     * @param avgDurationMs average duration of completed tasks in milliseconds
     * @throws IllegalArgumentException if any count value is negative or avgDurationMs is negative
     */
    public MigrationStatistics(int totalSubmitted, int completed, int failed,
                              int cancelled, int active, long avgDurationMs) {
        if (totalSubmitted < 0 || completed < 0 || failed < 0 ||
            cancelled < 0 || active < 0) {
            throw new IllegalArgumentException("All count values must be non-negative");
        }
        if (avgDurationMs < 0) {
            throw new IllegalArgumentException("Average duration must be non-negative");
        }

        this.totalSubmitted = totalSubmitted;
        this.completed = completed;
        this.failed = failed;
        this.cancelled = cancelled;
        this.active = active;
        this.avgDurationMs = avgDurationMs;
    }

    /**
     * Returns the total number of migration tasks submitted.
     *
     * @return total submitted tasks
     */
    public int getTotalSubmitted() {
        return totalSubmitted;
    }

    /**
     * Returns the number of successfully completed migration tasks.
     *
     * @return completed tasks count
     */
    public int getCompleted() {
        return completed;
    }

    /**
     * Returns the number of failed migration tasks.
     *
     * @return failed tasks count
     */
    public int getFailed() {
        return failed;
    }

    /**
     * Returns the number of cancelled migration tasks.
     *
     * @return cancelled tasks count
     */
    public int getCancelled() {
        return cancelled;
    }

    /**
     * Returns the number of currently active migration tasks.
     *
     * @return active tasks count
     */
    public int getActive() {
        return active;
    }

    /**
     * Returns the average duration of completed migration tasks.
     *
     * @return average duration in milliseconds
     */
    public long getAvgDurationMs() {
        return avgDurationMs;
    }

    /**
     * Calculates and returns the success rate of migration tasks.
     *
     * <p>Success rate is calculated as: completed / (completed + failed)
     * <p>Returns 0.0 if no tasks have been completed or failed.
     *
     * @return success rate as a double between 0.0 and 1.0
     */
    public double getSuccessRate() {
        int total = completed + failed;
        if (total == 0) {
            return 0.0;
        }
        return (double) completed / total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MigrationStatistics that = (MigrationStatistics) o;
        return totalSubmitted == that.totalSubmitted &&
               completed == that.completed &&
               failed == that.failed &&
               cancelled == that.cancelled &&
               active == that.active &&
               avgDurationMs == that.avgDurationMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalSubmitted, completed, failed, cancelled, active, avgDurationMs);
    }

    @Override
    public String toString() {
        return "MigrationStatistics{" +
               "totalSubmitted=" + totalSubmitted +
               ", completed=" + completed +
               ", failed=" + failed +
               ", cancelled=" + cancelled +
               ", active=" + active +
               ", avgDurationMs=" + avgDurationMs +
               '}';
    }
}
