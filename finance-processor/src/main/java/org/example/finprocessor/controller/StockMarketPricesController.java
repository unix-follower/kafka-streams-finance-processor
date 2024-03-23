package org.example.finprocessor.controller;

import org.apache.commons.lang3.tuple.Pair;
import org.example.finprocessor.api.GetPricesParams;
import org.example.finprocessor.api.PriceSearchMode;
import org.example.finprocessor.api.StockMarketPricesApi;
import org.example.finprocessor.api.StockPriceWindowResponse;
import org.example.finprocessor.component.StockMarketPricesControllerFacade;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.function.Function;
import java.util.stream.Stream;

@RestController
public class StockMarketPricesController implements StockMarketPricesApi {
    static final String KEY = "key";
    static final String KEYF_ROM = "keyFrom";
    static final String KEY_TO = "keyTo";
    static final String TIME_FROM = "timeFrom";
    static final String TIME_TO = "timeTo";
    static final String INVALID_SEARCH_MODE_ERR_MSG = "The search mode is unknown";
    static final String PARAM_IS_NULL_ERR_MSG_FORMAT = "The %s is null";

    private final EnumMap<PriceSearchMode, Function<GetPricesParams, ResponseStatusException>> validationMap =
        new EnumMap<>(PriceSearchMode.class);

    private final StockMarketPricesControllerFacade facade;

    public StockMarketPricesController(StockMarketPricesControllerFacade facade) {
        this.facade = facade;

        validationMap.put(PriceSearchMode.SESSION_FETCH, params -> {
            if (params.key() == null) {
                return new ResponseStatusException(
                    HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()),
                    String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, KEY)
                );
            }
            return null;
        });

        validationMap.put(PriceSearchMode.SESSION_FETCH_SESSION, this::validateKeyAndTimeRange);
        validationMap.put(PriceSearchMode.SESSION_FIND_SESSIONS, this::validateKeyAndTimeRange);
        validationMap.put(PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS, this::validateKeyAndTimeRange);

        validationMap.put(PriceSearchMode.FETCH, this::validateKeyAndTimeRange);
        validationMap.put(PriceSearchMode.FETCH_ALL, this::validateTimeRange);
        validationMap.put(PriceSearchMode.BACKWARD_FETCH_ALL, this::validateTimeRange);
        validationMap.put(PriceSearchMode.FETCH_KEY_RANGE, this::validateKeyRangeAndTimeRange);
        validationMap.put(PriceSearchMode.BACKWARD_FETCH, this::validateKeyAndTimeRange);
        validationMap.put(PriceSearchMode.BACKWARD_FETCH_KEY_RANGE, this::validateKeyRangeAndTimeRange);
    }

    private ResponseStatusException validateKeyAndTimeRange(GetPricesParams params) {
        final var validationResultOptional = Stream.<Pair<Object, String>>of(
                Pair.of(params.key(), KEY),
                Pair.of(params.timeFrom(), TIME_FROM),
                Pair.of(params.timeTo(), TIME_TO)
            )
            .filter(paramPair -> paramPair.getLeft() == null)
            .findFirst();

        if (validationResultOptional.isPresent()) {
            final var paramPair = validationResultOptional.get();
            return createResponseStatusException(paramPair);
        }

        return null;
    }

    private static ResponseStatusException createResponseStatusException(Pair<Object, String> paramPair) {
        return new ResponseStatusException(
            HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()),
            String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, paramPair.getRight())
        );
    }

    private ResponseStatusException validateKeyRangeAndTimeRange(GetPricesParams params) {
        final var validationResultOptional = Stream.<Pair<Object, String>>of(
                Pair.of(params.keyFrom(), KEYF_ROM),
                Pair.of(params.keyTo(), KEY_TO),
                Pair.of(params.timeFrom(), TIME_FROM),
                Pair.of(params.timeTo(), TIME_TO)
            )
            .filter(paramPair -> paramPair.getLeft() == null)
            .findFirst();

        if (validationResultOptional.isPresent()) {
            final var paramPair = validationResultOptional.get();
            return createResponseStatusException(paramPair);
        }

        return null;
    }

    private ResponseStatusException validateTimeRange(GetPricesParams params) {
        final var validationResultOptional = Stream.<Pair<Object, String>>of(
                Pair.of(params.timeFrom(), TIME_FROM),
                Pair.of(params.timeTo(), TIME_TO)
            )
            .filter(paramPair -> paramPair.getLeft() == null)
            .findFirst();

        if (validationResultOptional.isPresent()) {
            final var paramPair = validationResultOptional.get();
            return createResponseStatusException(paramPair);
        }

        return null;
    }

    @Override
    public Flux<StockPriceWindowResponse> getPrices(
        ServerWebExchange exchange,
        String mode,
        String key,
        String keyFrom,
        String keyTo,
        OffsetDateTime timeFrom,
        OffsetDateTime timeTo
    ) {
        final var modeOptional = PriceSearchMode.of(mode);
        if (modeOptional.isEmpty()) {
            return Flux.error(createErrorForUnknownSearchMode());
        }
        final var searchMode = modeOptional.get();

        final var pricesParams = new GetPricesParams(
            searchMode,
            key,
            keyFrom,
            keyTo,
            timeFrom,
            timeTo
        );
        return validateGetPricesParams(pricesParams)
            .flatMapMany(unused -> facade.getPrices(exchange, pricesParams));
    }

    private Mono<GetPricesParams> validateGetPricesParams(GetPricesParams params) {
        final var validationFn = validationMap.get(params.mode());
        if (validationFn != null) {
            final var responseStatusException = validationFn.apply(params);
            if (responseStatusException != null) {
                return Mono.error(responseStatusException);
            }
        }

        return Mono.just(params);
    }

    @Override
    public Flux<StockPriceWindowResponse> getPricesFromLocalStore(
        String mode,
        String key,
        String keyFrom,
        String keyTo,
        OffsetDateTime timeFrom,
        OffsetDateTime timeTo
    ) {
        final var modeOptional = PriceSearchMode.of(mode);
        if (modeOptional.isEmpty()) {
            return Flux.error(createErrorForUnknownSearchMode());
        }
        final var searchMode = modeOptional.get();

        final var pricesParams = new GetPricesParams(
            searchMode,
            key,
            keyFrom,
            keyTo,
            timeFrom,
            timeTo
        );
        return facade.getPricesFromLocalStore(pricesParams);
    }

    private static ResponseStatusException createErrorForUnknownSearchMode() {
        return new ResponseStatusException(
            HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()),
            INVALID_SEARCH_MODE_ERR_MSG
        );
    }
}
