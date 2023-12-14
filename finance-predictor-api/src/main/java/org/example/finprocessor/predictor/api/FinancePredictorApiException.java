package org.example.finprocessor.predictor.api;

public class FinancePredictorApiException extends RuntimeException {
    private final FinancePredictorErrorCode errorCode;

    public FinancePredictorApiException(FinancePredictorErrorCode errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    public FinancePredictorErrorCode getErrorCode() {
        return errorCode;
    }
}
