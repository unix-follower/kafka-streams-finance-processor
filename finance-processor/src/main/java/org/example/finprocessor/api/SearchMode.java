package org.example.finprocessor.api;

import java.util.Arrays;
import java.util.Optional;

public enum SearchMode {
    ALL("all"),
    PREFIX_SCAN("prefixScan"),
    RANGE("range"),
    REVERSE_RANGE("reverseRange"),
    REVERSE_ALL("reverseAll");

    private final String mode;

    SearchMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public static Optional<SearchMode> of(String value) {
        return Arrays.stream(SearchMode.values())
            .filter(mode -> mode.getMode().equals(value))
            .findFirst();
    }
}
