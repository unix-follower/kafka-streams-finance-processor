package org.example.finprocessor.api;

import java.util.Arrays;
import java.util.Optional;

public enum PredictionSearchMode {
    ALL("all"),
    PREFIX_SCAN("prefixScan"),
    RANGE("range"),
    REVERSE_RANGE("reverseRange"),
    REVERSE_ALL("reverseAll");

    private final String mode;

    PredictionSearchMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public static Optional<PredictionSearchMode> of(String value) {
        return Arrays.stream(PredictionSearchMode.values())
            .filter(mode -> mode.getMode().equals(value))
            .findFirst();
    }
}
