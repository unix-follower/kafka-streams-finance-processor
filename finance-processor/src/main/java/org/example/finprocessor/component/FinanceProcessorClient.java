package org.example.finprocessor.component;

import org.example.finprocessor.api.GetPredictionsParams;
import org.example.finprocessor.api.LossPredictionResponse;
import org.example.finprocessor.api.StockPricePredictionResponse;
import org.example.finprocessor.api.TopPredictionResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FinanceProcessorClient {
    Mono<StockPricePredictionResponse> getPredictionByTicker(String hostUrl, String ticker);

    Flux<StockPricePredictionResponse> getPredictions(
        String hostUrl,
        GetPredictionsParams params
    );

    Flux<TopPredictionResponse> getTopPredictions(String hostUrl);

    Flux<LossPredictionResponse> getLossPredictions(String hostUrl);
}
