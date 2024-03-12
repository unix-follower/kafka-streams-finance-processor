package org.example.finprocessor.component;

import org.example.finprocessor.api.ErrorCode;
import org.example.finprocessor.api.ErrorResponse;
import org.example.finprocessor.api.GetPredictionsParams;
import org.example.finprocessor.api.LossPredictionResponse;
import org.example.finprocessor.api.SearchMode;
import org.example.finprocessor.api.StockPricePredictionResponse;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.exception.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;

@Component
public class FinProcessorClient implements FinanceProcessorClient {
    private final WebClient financeProcessorWebClient;

    public FinProcessorClient(WebClient financeProcessorWebClient) {
        this.financeProcessorWebClient = financeProcessorWebClient;
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

    private static void addModeParam(SearchMode searchMode, UriBuilder uriBuilder) {
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

            if (searchMode == SearchMode.RANGE || searchMode == SearchMode.REVERSE_RANGE) {
                addFromParam(params.from(), uriBuilder);
                addToParam(params.to(), uriBuilder);
            } else if (searchMode == SearchMode.PREFIX_SCAN) {
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
