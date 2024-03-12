package org.example.finprocessor.component;

import java.math.BigDecimal;

public class LossCalculator {
    private LossCalculator() {
    }

    public static BigDecimal calculateLossDifference(BigDecimal value, double percent) {
        final var percentOfClose = value.multiply(BigDecimal.valueOf(percent));
        return value.subtract(percentOfClose);
    }

    public static boolean isLossThresholdExceeded(BigDecimal value, BigDecimal pricePrediction, double percent) {
        final var threshold = calculateLossDifference(value, percent);
        return threshold.compareTo(pricePrediction) >= 0;
    }
}
