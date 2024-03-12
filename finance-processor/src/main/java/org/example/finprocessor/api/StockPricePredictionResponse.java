package org.example.finprocessor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StockPricePredictionResponse(
    String ticker,
    OffsetDateTime openRangeAt,
    OffsetDateTime closedRangeAt,
    OffsetDateTime predictionAt,
    BigDecimal pricePrediction
) {
}
