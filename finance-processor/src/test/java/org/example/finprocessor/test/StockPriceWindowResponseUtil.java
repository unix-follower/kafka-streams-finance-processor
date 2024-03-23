package org.example.finprocessor.test;

import org.example.finprocessor.api.StockPriceWindowResponse;

import java.time.OffsetDateTime;
import java.util.Objects;

public class StockPriceWindowResponseUtil {
    private StockPriceWindowResponseUtil() {
    }

    public static boolean isLeftApproximatelyEqual(OffsetDateTime left, OffsetDateTime right) {
        if (left == null && right == null) {
            return true;
        } else if (left != null && right != null) {
            return right.isEqual(left) ||
                right.isBefore(left);
        } else {
            return false;
        }
    }

    public static boolean equalsWithApproximateWindows(StockPriceWindowResponse expected, StockPriceWindowResponse actual) {
        final boolean isEventTimeEqual = isLeftApproximatelyEqual(expected.eventTime(), actual.eventTime());
        final boolean isWindowStartEqual = isLeftApproximatelyEqual(expected.windowStart(), actual.windowStart());
        final boolean isWindowEndEqual = isLeftApproximatelyEqual(expected.windowEnd(), actual.windowEnd());
        return Objects.equals(actual.prices(), expected.prices()) &&
            isEventTimeEqual &&
            isWindowStartEqual &&
            isWindowEndEqual;
    }
}
