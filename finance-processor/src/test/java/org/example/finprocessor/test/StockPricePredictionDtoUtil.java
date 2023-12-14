package org.example.finprocessor.test;

import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;

import java.math.BigDecimal;
import java.util.Objects;

public class StockPricePredictionDtoUtil {
    private StockPricePredictionDtoUtil() {
    }

    public static boolean equalsWithPredictionAtGtOrEq(StockPricePredictionDto expected, StockPricePredictionDto actual) {
        return Objects.equals(actual.ticker(), expected.ticker()) &&
            Objects.equals(actual.openRangeAt(), expected.openRangeAt()) &&
            Objects.equals(actual.close(), expected.close()) &&
            Objects.equals(actual.closedRangeAt(), expected.closedRangeAt()) &&
            Objects.equals(actual.pricePrediction(), expected.pricePrediction()) &&
            (
                actual.predictionAt().isEqual(expected.predictionAt()) ||
                    actual.predictionAt().isBefore(expected.predictionAt())
            );
    }

    public static BigDecimal closePlus1(StockPrice stockPrice) {
        return stockPrice.close().add(BigDecimal.ONE);
    }
}
