package org.example.finprocessor.api;

public record GetPredictionsParams(
    SearchMode mode,
    String from,
    String to,
    String prefix
) {
}
