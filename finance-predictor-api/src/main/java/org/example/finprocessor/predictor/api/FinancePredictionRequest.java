package org.example.finprocessor.predictor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record FinancePredictionRequest(
    String ticker,
    OffsetDateTime openRangeAt,
    OffsetDateTime closedRangeAt,
    List<BigDecimal> prices
) {
}
