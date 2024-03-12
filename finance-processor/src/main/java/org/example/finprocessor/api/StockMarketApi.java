package org.example.finprocessor.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequestMapping("/api/v1")
public interface StockMarketApi {
    @GetMapping("/predictions")
    Flux<StockPricePredictionResponse> getPredictions(
        ServerWebExchange exchange,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String prefix
    );

    @GetMapping("/local/predictions")
    Flux<StockPricePredictionResponse> getPredictionsFromLocalStore(
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String prefix
    );

    @GetMapping("/predictions/{ticker}")
    Mono<StockPricePredictionResponse> getPredictionByTicker(
        ServerWebExchange exchange, @PathVariable String ticker
    );

    @GetMapping("/local/top/predictions")
    Flux<TopPredictionResponse> getTopPredictionsFromLocalStore();

    @GetMapping("/top/predictions")
    Flux<TopPredictionResponse> getTopPredictions(ServerWebExchange exchange);

    @GetMapping("/local/loss/predictions")
    Flux<LossPredictionResponse> getLossPredictionsFromLocalStore();

    @GetMapping("/loss/predictions")
    Flux<LossPredictionResponse> getLossPredictions(ServerWebExchange exchange);
}
