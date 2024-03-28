package org.example.finprocessor.api;

import java.util.Arrays;
import java.util.Optional;

public enum PriceSearchMode {
    // ------------------
    // Time-based modes
    // ------------------
    ALL("all"),
    BACKWARD_ALL("backwardAll"),
    FETCH("fetch"),
    FETCH_KEY_RANGE("fetchKeyRange"),
    FETCH_ALL("fetchAll"),
    BACKWARD_FETCH("backwardFetch"),
    BACKWARD_FETCH_ALL("backwardFetchAll"),
    BACKWARD_FETCH_KEY_RANGE("backwardFetchKeyRange"),
    // ---------------------
    // Session-based modes
    // ---------------------
    SESSION_FETCH("sFetch"),
    SESSION_FETCH_SESSION("sFetchSession"),
    SESSION_FIND_SESSIONS("sFindSessions"),
    SESSION_BACKWARD_FETCH_KEY_RANGE("sBackwardFetchKeyRange"),
    SESSION_BACKWARD_FETCH("sBackwardFetch"),
    SESSION_BACKWARD_FIND_SESSIONS("sBackwardFindSessions"),
    SESSION_BACKWARD_FIND_SESSIONS_KEY_RANGE("sBackwardFindSessionsKeyRange");

    private final String mode;

    PriceSearchMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public static Optional<PriceSearchMode> of(String value) {
        return Arrays.stream(PriceSearchMode.values())
            .filter(mode -> mode.getMode().equals(value))
            .findFirst();
    }
}
