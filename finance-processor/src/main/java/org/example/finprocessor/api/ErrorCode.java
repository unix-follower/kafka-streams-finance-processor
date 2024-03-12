package org.example.finprocessor.api;

public enum ErrorCode {
    UNKNOWN(0),
    TICKER_NOT_FOUND(1);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
