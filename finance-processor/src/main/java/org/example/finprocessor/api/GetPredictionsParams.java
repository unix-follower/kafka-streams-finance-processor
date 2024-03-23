package org.example.finprocessor.api;

public record GetPredictionsParams(
    PredictionSearchMode mode,
    String from,
    String to,
    String prefix
) {
}
