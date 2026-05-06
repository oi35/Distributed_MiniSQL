package com.minisql.client.exception;

import com.minisql.common.proto.ErrorCode;

public class MiniSQLClientException extends RuntimeException {

    private final ErrorCode errorCode;

    public MiniSQLClientException(String message) {
        this(message, ErrorCode.ERROR_UNKNOWN, null);
    }

    public MiniSQLClientException(String message, ErrorCode errorCode) {
        this(message, errorCode, null);
    }

    public MiniSQLClientException(String message, ErrorCode errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null ? ErrorCode.ERROR_UNKNOWN : errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
