package org.example.finprocessor.stockmarket.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StockPricePredictionDto(
    String ticker,
    OffsetDateTime openRangeAt,
    BigDecimal close,
    OffsetDateTime closedRangeAt,
    OffsetDateTime predictionAt,
    BigDecimal pricePrediction
) {
}
