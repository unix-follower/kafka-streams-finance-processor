package org.example.finprocessor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceProcessorTest {
    @Test
    void test_initDebugAgentsIfNotProd() {
        assertDoesNotThrow(FinanceProcessor::initDebugAgentsIfNotProd);
    }

    @Test
    void test_isProdEnv() {
        assertTrue(FinanceProcessor.isProdEnv("prod"));
    }

    @Test
    void test_isProdEnv_false() {
        assertFalse(FinanceProcessor.isProdEnv("dev"));
    }

    @Test
    void test_isBlockHoundEnabled() {
        assertTrue(FinanceProcessor.isBlockHoundEnabled("true"));
    }

    @Test
    void test_isBlockHoundEnabled_false() {
        assertFalse(FinanceProcessor.isBlockHoundEnabled("false"));
    }

    @Test
    void test_isBlockHoundEnabled_with_not_boolean_value() {
        assertFalse(FinanceProcessor.isBlockHoundEnabled("disable"));
    }
}
