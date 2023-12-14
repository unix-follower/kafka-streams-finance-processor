package org.example.finprocessor.api;

import java.math.BigDecimal;

public record LossPredictionResponse(
    String ticker,
    BigDecimal pricePrediction
) {
}
