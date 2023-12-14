package org.example.finprocessor.exception;

import org.example.finprocessor.api.ErrorCode;

public class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public AppException(Throwable cause, ErrorCode errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
