package com.minisql.master.balance;

/**
 * Region迁移异常
 */
public class MigrationException extends Exception {

    private final boolean recoverable;

    public MigrationException(String message) {
        this(message, false);
    }

    public MigrationException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public MigrationException(String message, boolean recoverable) {
        super(message);
        this.recoverable = recoverable;
    }

    public MigrationException(String message, Throwable cause, boolean recoverable) {
        super(message, cause);
        this.recoverable = recoverable;
    }

    public boolean isRecoverable() {
        return recoverable;
    }
}
