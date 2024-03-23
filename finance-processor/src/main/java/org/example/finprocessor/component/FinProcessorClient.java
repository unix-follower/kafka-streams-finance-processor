package org.example.finprocessor.component;

import org.example.finprocessor.api.ErrorCode;
import org.example.finprocessor.api.ErrorResponse;
import org.example.finprocessor.api.GetPredictionsParams;
import org.example.finprocessor.api.GetPricesParams;
import org.example.finprocessor.api.LossPredictionResponse;
import org.example.finprocessor.api.PredictionSearchMode;
import org.example.finprocessor.api.PriceSearchMode;
import org.example.finprocessor.api.StockPricePredictionResponse;
import org.example.finprocessor.api.StockPriceWindowResponse;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.exception.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.EnumMap;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Component
public class FinProcessorClient implements FinanceProcessorClient {
    private final WebClient financeProcessorWebClient;

    private final EnumMap<PriceSearchMode, BiConsumer<UriBuilder, GetPricesParams>> priceSearchModeMap =
        new EnumMap<>(PriceSearchMode.class);

    public FinProcessorClient(WebClient financeProcessorWebClient) {
        this.financeProcessorWebClient = financeProcessorWebClient;

        priceSearchModeMap.put(PriceSearchMode.SESSION_FETCH, this::addKeyParam);
        priceSearchModeMap.put(PriceSearchMode.SESSION_FETCH_SESSION, this::addKeyWithTimeRangeParams);
        priceSearchModeMap.put(PriceSearchMode.SESSION_FIND_SESSIONS, this::addKeyWithTimeRangeParams);
        priceSearchModeMap.put(PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS, this::addKeyWithTimeRangeParams);

        priceSearchModeMap.put(PriceSearchMode.FETCH, this::addKeyWithTimeRangeParams);
        priceSearchModeMap.put(PriceSearchMode.FETCH_KEY_RANGE, this::addKeyRangeWithTimeRangeParams);
        priceSearchModeMap.put(PriceSearchMode.FETCH_ALL, this::addTimeRangeParams);
        priceSearchModeMap.put(PriceSearchMode.BACKWARD_FETCH_ALL, this::addTimeRangeParams);
        priceSearchModeMap.put(PriceSearchMode.BACKWARD_FETCH, this::addKeyWithTimeRangeParams);
        priceSearchModeMap.put(PriceSearchMode.BACKWARD_FETCH_KEY_RANGE, this::addKeyRangeWithTimeRangeParams);
    }

    @Override
    public Flux<StockPriceWindowResponse> getPrices(String hostUrl, GetPricesParams params) {
        final var uriFunction = createGetPricesUriFn(params);

        return financeProcessorWebClient.get()
            .uri(hostUrl + "/api/v1/local/prices", uriFunction)
            .retrieve()
            .bodyToFlux(StockPriceWindowResponse.class);
    }

    private void addKeyParam(UriBuilder uriBuilder, GetPricesParams params) {
        uriBuilder.queryParam("key", params.key());
    }

    private void addKeyWithTimeRangeParams(UriBuilder uriBuilder, GetPricesParams params) {
        addKeyParam(uriBuilder, params);
        addTimeRangeParams(uriBuilder, params);
    }
    private void addTimeRangeParams(UriBuilder uriBuilder, GetPricesParams params) {
        uriBuilder.queryParam("timeFrom", params.timeFrom())
            .queryParam("timeTo", params.timeTo());
    }

    private void addKeyRangeWithTimeRangeParams(UriBuilder uriBuilder, GetPricesParams params) {
        uriBuilder.queryParam("keyFrom", params.keyFrom())
            .queryParam("keyTo", params.keyTo());
        addTimeRangeParams(uriBuilder, params);
    }

    Function<UriBuilder, URI> createGetPricesUriFn(GetPricesParams params) {
        return uriBuilder -> {
            final var searchMode = params.mode();
            final var mode = searchMode.getMode();

            uriBuilder.queryParam("mode", mode);
            Optional.ofNullable(priceSearchModeMap.get(searchMode))
                .ifPresent(paramsHandler -> paramsHandler.accept(uriBuilder, params));

            return uriBuilder.build();
        };
    }

    @Override
    public Mono<StockPricePredictionResponse> getPredictionByTicker(String hostUrl, String ticker) {
        return financeProcessorWebClient.get()
            .uri(hostUrl + "/api/v1/predictions/{ticker}", ticker)
            .retrieve()
            .bodyToMono(StockPricePredictionResponse.class)
            .onErrorResume(
                WebClientResponseException.class::isInstance,
                throwable -> Mono.error(translateError((WebClientResponseException) throwable))
            );
    }

    private RuntimeException translateError(WebClientResponseException exception) {
        RuntimeException e = exception;
        final var errorResponse = exception
            .getResponseBodyAs(ErrorResponse.class);
        if (errorResponse != null) {
            final var errorCode = errorResponse.errorCode();
            if (errorCode == ErrorCode.TICKER_NOT_FOUND) {
                e = new EntityNotFoundException(ErrorCode.TICKER_NOT_FOUND);
            }
        }
        return e;
    }

    private static boolean isNotNullAndNotBlank(String param) {
        return param != null && !param.isBlank();
    }

    private static void addModeParam(PredictionSearchMode searchMode, UriBuilder uriBuilder) {
        final var mode = searchMode.getMode();
        uriBuilder.queryParam("mode", mode);
    }

    private static void addFromParam(String from, UriBuilder uriBuilder) {
        if (isNotNullAndNotBlank(from)) {
            uriBuilder.queryParam("from", from);
        }
    }

    private static void addToParam(String to, UriBuilder uriBuilder) {
        if (isNotNullAndNotBlank(to)) {
            uriBuilder.queryParam("to", to);
        }
    }

    static Function<UriBuilder, URI> createGetPredictionsUriFn(GetPredictionsParams params) {
        return uriBuilder -> {
            final var searchMode = params.mode();
            addModeParam(searchMode, uriBuilder);

            if (searchMode == PredictionSearchMode.RANGE || searchMode == PredictionSearchMode.REVERSE_RANGE) {
                addFromParam(params.from(), uriBuilder);
                addToParam(params.to(), uriBuilder);
            } else if (searchMode == PredictionSearchMode.PREFIX_SCAN) {
                uriBuilder.queryParam("prefix", params.prefix());
            }

            return uriBuilder.build();
        };
    }

    @Override
    public Flux<StockPricePredictionResponse> getPredictions(String hostUrl, GetPredictionsParams params) {
        final var uriFunction = createGetPredictionsUriFn(params);

        return financeProcessorWebClient.get()
            .uri(hostUrl + "/api/v1/local/predictions", uriFunction)
            .retrieve()
            .bodyToFlux(StockPricePredictionResponse.class);
    }

    @Override
    public Flux<TopPredictionResponse> getTopPredictions(String hostUrl) {
        return financeProcessorWebClient.get()
            .uri(hostUrl + "/api/v1/local/top/predictions")
            .retrieve()
            .bodyToFlux(TopPredictionResponse.class);
    }

    @Override
    public Flux<LossPredictionResponse> getLossPredictions(String hostUrl) {
        return financeProcessorWebClient.get()
            .uri(hostUrl + "/api/v1/local/loss/predictions")
            .retrieve()
            .bodyToFlux(LossPredictionResponse.class);
    }
}
