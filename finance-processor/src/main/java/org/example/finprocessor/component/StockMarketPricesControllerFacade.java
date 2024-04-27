package org.example.finprocessor.component;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlySessionStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.example.finprocessor.api.GetPricesParams;
import org.example.finprocessor.api.PriceSearchMode;
import org.example.finprocessor.api.StockPriceWindowResponse;
import org.example.finprocessor.config.AppConfig;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.stream.WindowType;
import org.example.finprocessor.util.Constants;
import org.example.finprocessor.util.LoggerUtil;
import org.example.finprocessor.util.ServerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.logging.Level;

@Component
public class StockMarketPricesControllerFacade {
    private static final Logger logger = LoggerFactory.getLogger(StockMarketPricesControllerFacade.class);

    private final Map<PriceSearchMode, BiConsumer<FluxSink<StockPriceWindowResponse>, GetPricesParams>> searchModeMap =
        new EnumMap<>(PriceSearchMode.class);

    private final StreamsBuilderFactoryBean factoryBean;
    private final AppProperties appProperties;
    private final FinanceProcessorClient financeProcessorClient;
    private final String storeName;

    public StockMarketPricesControllerFacade(
        StreamsBuilderFactoryBean factoryBean,
        AppProperties appProperties,
        FinanceProcessorClient financeProcessorClient
    ) {
        this.factoryBean = factoryBean;
        this.financeProcessorClient = financeProcessorClient;
        this.appProperties = appProperties;

        final var streamProperties = appProperties.stockMarketStream();
        if (WindowType.SESSION == streamProperties.windowType()) {
            addSessionSearchModes();
            storeName = Constants.PREDICTIONS_SESSION_WINDOWED_STORE;
        } else {
            addTimeSearchModes();
            storeName = Constants.PREDICTIONS_TIME_WINDOWED_STORE;
        }
    }

    private void addTimeSearchModes() {
        searchModeMap.put(PriceSearchMode.ALL, (fluxSink, params) -> {
            final var store = getStockPricesTimestampedWindowStore();
            final var iterator = store.all();
            emitWindowedPricesWithTimestamp(iterator, fluxSink);
        });
        searchModeMap.put(PriceSearchMode.BACKWARD_ALL, (fluxSink, params) -> {
            final var store = getStockPricesTimestampedWindowStore();
            final var iterator = store.backwardAll();
            emitWindowedPricesWithTimestamp(iterator, fluxSink);
        });
        searchModeMap.put(PriceSearchMode.FETCH, (fluxSink, params) -> {
            final var store = getStockPricesTimestampedWindowStore();
            final var iterator = store.fetch(
                params.key(),
                params.timeFrom().toInstant(),
                params.timeTo().toInstant()
            );
            emitWindowedPricesWithTimestamp(iterator, fluxSink);
        });
        searchModeMap.put(PriceSearchMode.FETCH_KEY_RANGE, (fluxSink, params) -> {
            final var store = getStockPricesTimestampedWindowStore();
            final var iterator = store.fetch(
                params.keyFrom(), params.keyTo(),
                params.timeFrom().toInstant(),
                params.timeTo().toInstant()
            );
            emitWindowedPricesWithTimestamp(iterator, fluxSink);
        });
        searchModeMap.put(PriceSearchMode.FETCH_ALL, (fluxSink, params) -> {
            final var store = getStockPricesTimestampedWindowStore();
            final var iterator = store.fetchAll(
                params.timeFrom().toInstant(),
                params.timeTo().toInstant()
            );
            emitWindowedPricesWithTimestamp(iterator, fluxSink);
        });
        searchModeMap.put(PriceSearchMode.BACKWARD_FETCH_ALL, (fluxSink, params) -> {
            final var store = getStockPricesTimestampedWindowStore();
            final var iterator = store.backwardFetchAll(
                params.timeFrom().toInstant(),
                params.timeTo().toInstant()
            );
            emitWindowedPricesWithTimestamp(iterator, fluxSink);
        });
        searchModeMap.put(PriceSearchMode.BACKWARD_FETCH, (fluxSink, params) -> {
            final var store = getStockPricesTimestampedWindowStore();
            final var iterator = store.backwardFetch(
                params.key(),
                params.timeFrom().toInstant(),
                params.timeTo().toInstant()
            );
            emitWindowedPricesWithTimestamp(iterator, fluxSink);
        });
        searchModeMap.put(PriceSearchMode.BACKWARD_FETCH_KEY_RANGE, (fluxSink, params) -> {
            final var store = getStockPricesTimestampedWindowStore();

            final var iterator = store.backwardFetch(
                params.keyFrom(), params.keyTo(),
                params.timeFrom().toInstant(),
                params.timeTo().toInstant()
            );
            emitWindowedPricesWithTimestamp(iterator, fluxSink);
        });
    }

    private Mono<List<String>> getRemoteStoreHosts(ServerHttpRequest request, String storeName) {
        return Mono.fromSupplier(() -> {
            final var metadataCollection = getKafkaStreams().streamsMetadataForStore(storeName);
            final var urls = metadataCollection.stream()
                .filter(streamsMetadata -> !ServerUtil.isSameHost(streamsMetadata.hostInfo(), request))
                .map(metadata -> String.format(Constants.REMOTE_HOST_FORMAT, metadata.host(), metadata.port()))
                .toList();
            LoggerUtil.printStreamsAppHosts(logger, request, urls);
            return urls;
        });
    }

    private void addSessionSearchModes() {
        searchModeMap.put(PriceSearchMode.SESSION_FETCH, (fluxSink, params) -> {
            final var store = getStockPricesSessionStore();
            final var iterator = store.fetch(params.key());
            emitWindowedPrices(iterator, fluxSink);
        });
        searchModeMap.put(PriceSearchMode.SESSION_FETCH_SESSION, (fluxSink, params) -> {
            final var store = getStockPricesSessionStore();
            final var stockPrices = store.fetchSession(
                params.key(),
                params.timeFrom().toInstant().toEpochMilli(),
                params.timeTo().toInstant().toEpochMilli()
            );
            final var response = new StockPriceWindowResponse(
                null, null, null,
                stockPrices
            );
            fluxSink.next(response);
        });
        searchModeMap.put(PriceSearchMode.SESSION_FIND_SESSIONS, (fluxSink, params) -> {
            final var store = getStockPricesSessionStore();
            final var iterator = store.findSessions(
                params.key(),
                params.timeFrom().toInstant().toEpochMilli(),
                params.timeTo().toInstant().toEpochMilli()
            );
            emitWindowedPrices(iterator, fluxSink);
        });
        searchModeMap.put(PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS, (fluxSink, params) -> {
            final var store = getStockPricesSessionStore();
            final var iterator = store.backwardFindSessions(
                params.key(),
                params.timeFrom().toInstant().toEpochMilli(),
                params.timeTo().toInstant().toEpochMilli()
            );
            emitWindowedPrices(iterator, fluxSink);
        });
    }

    private KafkaStreams getKafkaStreams() {
        return Objects.requireNonNull(factoryBean.getKafkaStreams());
    }

    private ReadOnlySessionStore<String, List<StockPrice>> getStockPricesSessionStore() {
        final var kafkaStreams = getKafkaStreams();
        return kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                Constants.PREDICTIONS_SESSION_WINDOWED_STORE,
                QueryableStoreTypes.sessionStore()
            )
        );
    }

    private ReadOnlyWindowStore<String, ValueAndTimestamp<List<StockPrice>>> getStockPricesTimestampedWindowStore() {
        final var kafkaStreams = getKafkaStreams();
        return kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                Constants.PREDICTIONS_TIME_WINDOWED_STORE,
                QueryableStoreTypes.timestampedWindowStore()
            )
        );
    }

    public Flux<StockPriceWindowResponse> getPrices(
        ServerWebExchange exchange,
        GetPricesParams params
    ) {
        final var pricesFromRemoteStoreFlux = getRemoteStoreHosts(
            exchange.getRequest(), storeName
        )
            .flatMapIterable(urls -> urls)
            .flatMap(url -> financeProcessorClient.getPrices(url, params));

        final var pricesFromLocalStoreFlux = getPricesFromLocalStore(params);

        return Flux.merge(pricesFromRemoteStoreFlux, pricesFromLocalStoreFlux)
            .log("getPrices", Level.INFO, true, SignalType.values())
            .timeout(AppConfig.DEFAULT_PUBLISHER_TIMEOUT);
    }

    public Flux<StockPriceWindowResponse> getPricesFromLocalStore(GetPricesParams params) {
        return Flux.<StockPriceWindowResponse>create(fluxSink -> {
                final var mode = params.mode();
                final var handler = searchModeMap.get(mode);
                if (handler != null) {
                    handler.accept(fluxSink, params);
                } else {
                    final var streamProperties = appProperties.stockMarketStream();
                    logger.warn(String.format(
                        "Cannot find handler for mode %s. The current window type is %s",
                        mode, streamProperties.windowType().name()
                    ));
                }

                fluxSink.complete();
            })
            .log("getPricesFromLocalStore", Level.INFO, true, SignalType.values());
    }

    private static void emitWindowedPrices(
        KeyValueIterator<Windowed<String>, List<StockPrice>> iterator,
        FluxSink<StockPriceWindowResponse> fluxSink
    ) {
        try (iterator) {
            while (iterator.hasNext()) {
                final var next = iterator.next();
                final var window = next.key.window();
                final var response = new StockPriceWindowResponse(
                    null,
                    window.startTime().atOffset(ZoneOffset.UTC),
                    window.endTime().atOffset(ZoneOffset.UTC),
                    next.value
                );
                fluxSink.next(response);
            }
        }
    }

    private static void emitWindowedPricesWithTimestamp(
        KeyValueIterator<Windowed<String>, ValueAndTimestamp<List<StockPrice>>> iterator,
        FluxSink<StockPriceWindowResponse> fluxSink
    ) {
        try (iterator) {
            while (iterator.hasNext()) {
                final var next = iterator.next();
                final var window = next.key.window();
                final var value = next.value;
                final var eventTime = timestampToOffsetDateTimeAtUTC(value);
                final var response = new StockPriceWindowResponse(
                    eventTime,
                    window.startTime().atOffset(ZoneOffset.UTC),
                    window.endTime().atOffset(ZoneOffset.UTC),
                    value.value()
                );
                fluxSink.next(response);
            }
        }
    }

    private static OffsetDateTime timestampToOffsetDateTimeAtUTC(ValueAndTimestamp<List<StockPrice>> value) {
        return Instant.ofEpochMilli(value.timestamp()).atOffset(ZoneOffset.UTC);
    }

    private static void emitWindowedPricesWithTimestamp(
        WindowStoreIterator<ValueAndTimestamp<List<StockPrice>>> iterator,
        FluxSink<StockPriceWindowResponse> fluxSink
    ) {
        try (iterator) {
            while (iterator.hasNext()) {
                final var next = iterator.next();
                final var value = next.value;
                final var eventTime = timestampToOffsetDateTimeAtUTC(value);
                final var response = new StockPriceWindowResponse(
                    eventTime,
                    null, null,
                    value.value()
                );
                fluxSink.next(response);
            }
        }
    }
}
