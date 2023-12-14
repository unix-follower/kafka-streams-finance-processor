package org.example.finprocessor.predictor;

import io.netty.handler.timeout.ReadTimeoutException;
import org.example.finprocessor.predictor.api.FinancePredictionRequest;
import org.example.finprocessor.predictor.api.FinancePredictionResponse;
import org.example.finprocessor.predictor.api.FinancePredictorApiException;
import org.example.finprocessor.predictor.api.FinancePredictorClient;
import org.example.finprocessor.predictor.api.FinancePredictorErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;

@Component
public class FinPredictorClient implements FinancePredictorClient {
    private final WebClient financePredictorWebClient;

    public FinPredictorClient(WebClient financePredictorWebClient) {
        this.financePredictorWebClient = financePredictorWebClient;
    }

    @Override
    public Mono<FinancePredictionResponse> predict(FinancePredictionRequest request) {
        return financePredictorWebClient.post()
            .uri("/api/v1/predict")
            .body(BodyInserters.fromValue(request))
            .retrieve()
            .bodyToMono(FinancePredictionResponse.class)
            .onErrorResume(this::errorPredicate, e -> Mono.error(
                new FinancePredictorApiException(resolveErrorCode(e), e)
            ));
    }

    private boolean errorPredicate(Throwable t) {
        return t instanceof WebClientRequestException || t instanceof WebClientResponseException;
    }

    private FinancePredictorErrorCode resolveErrorCode(Throwable throwable) {
        final var cause = throwable.getCause();
        var errorCode = FinancePredictorErrorCode.API_CALL_FAILED;

        if (cause != null
            && (cause instanceof ConnectException || cause.getCause() instanceof ConnectException)
        ) {
            errorCode = FinancePredictorErrorCode.CONNECTION_FAILED;
        } else if (cause instanceof ReadTimeoutException) {
            errorCode = FinancePredictorErrorCode.READ_TIMEOUT_FAILED;
        }
        return errorCode;
    }
}
