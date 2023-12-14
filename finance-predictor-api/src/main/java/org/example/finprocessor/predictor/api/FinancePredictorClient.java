package org.example.finprocessor.predictor.api;

import reactor.core.publisher.Mono;

public interface FinancePredictorClient {
    Mono<FinancePredictionResponse> predict(FinancePredictionRequest request);
}
