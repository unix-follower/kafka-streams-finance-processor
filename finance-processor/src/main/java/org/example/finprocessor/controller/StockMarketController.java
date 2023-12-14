package org.example.finprocessor.controller;

import org.example.finprocessor.api.GetPredictionsParams;
import org.example.finprocessor.api.LossPredictionResponse;
import org.example.finprocessor.api.SearchMode;
import org.example.finprocessor.api.StockMarketApi;
import org.example.finprocessor.api.StockPricePredictionResponse;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.component.StockMarketControllerFacade;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
public class StockMarketController implements StockMarketApi {
    private final StockMarketControllerFacade facade;

    public StockMarketController(StockMarketControllerFacade facade) {
        this.facade = facade;
    }

    private static SearchMode parseSearchMode(String mode) {
        return SearchMode.of(mode).orElse(SearchMode.ALL);
    }

    private static Optional<ResponseStatusException> validateSearchModeParam(String prefix, SearchMode searchMode) {
        if (searchMode == SearchMode.PREFIX_SCAN && prefix == null) {
            final var responseStatusException = new ResponseStatusException(
                HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()),
                "The prefix is not provided"
            );
            return Optional.of(responseStatusException);
        }
        return Optional.empty();
    }

    @Override
    public Flux<StockPricePredictionResponse> getPredictions(
        ServerWebExchange exchange,
        String mode,
        String from,
        String to,
        String prefix
    ) {
        final var searchMode = parseSearchMode(mode);
        final var responseStatusExceptionOptional = validateSearchModeParam(prefix, searchMode);
        if (responseStatusExceptionOptional.isPresent()) {
            return Flux.error(responseStatusExceptionOptional.get());
        }

        final var predictionParams = new GetPredictionsParams(
            searchMode,
            from,
            to,
            prefix
        );
        return facade.getPredictions(exchange, predictionParams);
    }

    @Override
    public Flux<StockPricePredictionResponse> getPredictionsFromLocalStore(
        String mode,
        String from,
        String to,
        String prefix
    ) {
        final var searchMode = parseSearchMode(mode);
        final var responseStatusExceptionOptional = validateSearchModeParam(prefix, searchMode);
        if (responseStatusExceptionOptional.isPresent()) {
            return Flux.error(responseStatusExceptionOptional.get());
        }

        final var predictionParams = new GetPredictionsParams(
            searchMode,
            from,
            to,
            prefix
        );
        return facade.getPredictionsFromLocalStore(predictionParams);
    }

    @Override
    public Mono<StockPricePredictionResponse> getPredictionByTicker(
        ServerWebExchange exchange, String ticker
    ) {
        return facade.getPredictionByTicker(exchange, ticker);
    }

    @Override
    public Flux<TopPredictionResponse> getTopPredictionsFromLocalStore() {
        return facade.getTopPredictionsFromLocalStore();
    }

    @Override
    public Flux<TopPredictionResponse> getTopPredictions(ServerWebExchange exchange) {
        return facade.getTopPredictions(exchange);
    }

    @Override
    public Flux<LossPredictionResponse> getLossPredictionsFromLocalStore() {
        return facade.getLossPredictionsFromLocalStore();
    }

    @Override
    public Flux<LossPredictionResponse> getLossPredictions(ServerWebExchange exchange) {
        return facade.getLossPredictions(exchange);
    }
}
