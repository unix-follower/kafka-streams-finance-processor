package org.example.finprocessor.component;

import org.example.finprocessor.test.AppPropertiesFactory;
import org.example.finprocessor.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class LossCalculatorTest {
    private BigDecimal close;

    @BeforeEach
    void setUp() {
        // 20% of 448.83 = 89.766
        // 448.83 - 89.766 = 359.064 threshold
        close = BigDecimal.valueOf(448.83);
    }

    private void test_threshold_is_not_hit(double prediction) {
        // given
        final var pricePrediction = BigDecimal.valueOf(prediction);
        // when
        final boolean isLossThresholdExceeded = LossCalculator
            .isLossThresholdExceeded(close, pricePrediction, AppPropertiesFactory.PERCENT_20);
        // then
        assertFalse(isLossThresholdExceeded);
    }

    @Test
    void test_20percent_loss_close_is_equal_to_prediction() {
        test_threshold_is_not_hit(448.83);
    }

    @Test
    void test_20percent_loss_prediction_is_lower_at_1_percent() {
        // given
        // 1% of 448.83 = 4.4883
        // 448.83 - 4.4883 = 444.3417
        test_threshold_is_not_hit(444.3417);
    }

    private void test_threshold_is_hit(double prediction) {
        // given
        final var pricePrediction = BigDecimal.valueOf(prediction);
        // when
        final boolean isLossThresholdExceeded = LossCalculator
            .isLossThresholdExceeded(close, pricePrediction, AppPropertiesFactory.PERCENT_20);
        // then
        assertTrue(isLossThresholdExceeded);
    }

    @Test
    void test_20percent_loss_prediction_is_lower_at_20_percent() {
        test_threshold_is_hit(359.064);
    }

    @Test
    void test_20percent_loss_prediction_is_lower_at_21_percent() {
        // given
        // 21% of 448.83 = 94.2543
        // 448.83 - 94.2543 = 354.5757
        test_threshold_is_hit(354.5757);
    }
}
