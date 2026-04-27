package com.minisql.master.balance;

import java.util.Objects;

/**
 * Tracks the progress of data synchronization during Region migration.
 */
public class SyncProgress {

    private final long totalBytes;
    private final long syncedBytes;
    private final boolean completed;

    /**
     * Creates a new SyncProgress instance.
     *
     * @param totalBytes total bytes to sync
     * @param syncedBytes bytes already synced
     * @param completed whether synchronization is completed
     * @throws IllegalArgumentException if totalBytes or syncedBytes is negative,
     *         or if syncedBytes exceeds totalBytes
     */
    public SyncProgress(long totalBytes, long syncedBytes, boolean completed) {
        if (totalBytes < 0) {
            throw new IllegalArgumentException("totalBytes cannot be negative");
        }
        if (syncedBytes < 0) {
            throw new IllegalArgumentException("syncedBytes cannot be negative");
        }
        if (syncedBytes > totalBytes) {
            throw new IllegalArgumentException("syncedBytes cannot exceed totalBytes");
        }
        this.totalBytes = totalBytes;
        this.syncedBytes = syncedBytes;
        this.completed = completed;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getSyncedBytes() {
        return syncedBytes;
    }

    public boolean isCompleted() {
        return completed;
    }

    /**
     * Calculates synchronization progress as a percentage.
     *
     * @return progress value between 0.0 and 1.0, or 0.0 if totalBytes is 0
     */
    public double getProgress() {
        if (totalBytes == 0) {
            return 0.0;
        }
        return (double) syncedBytes / totalBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SyncProgress that = (SyncProgress) o;
        return totalBytes == that.totalBytes &&
               syncedBytes == that.syncedBytes &&
               completed == that.completed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalBytes, syncedBytes, completed);
    }

    @Override
    public String toString() {
        return "SyncProgress{" +
               "totalBytes=" + totalBytes +
               ", syncedBytes=" + syncedBytes +
               ", completed=" + completed +
               '}';
    }
}
