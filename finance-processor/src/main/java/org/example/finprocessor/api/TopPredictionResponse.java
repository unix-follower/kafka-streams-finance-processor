package org.example.finprocessor.api;

import java.math.BigDecimal;
import java.util.Comparator;

public record TopPredictionResponse(
    String ticker,
    BigDecimal pricePrediction
) implements Comparable<TopPredictionResponse> {
    @Override
    public int compareTo(TopPredictionResponse prediction) {
        return Comparator.comparing(TopPredictionResponse::pricePrediction)
            .thenComparing(o -> o.ticker)
            .compare(this, prediction);
    }
}
