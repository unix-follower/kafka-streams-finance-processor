package org.example.finprocessor.predictor.api;

import java.math.BigDecimal;

public record FinancePredictionResponse(
    BigDecimal prediction
) {
}
