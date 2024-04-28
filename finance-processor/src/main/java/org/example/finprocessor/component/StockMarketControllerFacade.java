package org.example.finprocessor.component;

import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.example.finprocessor.api.GetPredictionsParams;
import org.example.finprocessor.api.LossPredictionResponse;
import org.example.finprocessor.api.StockPricePredictionResponse;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.config.AppConfig;
import org.example.finprocessor.exception.EntityNotFoundException;
import org.example.finprocessor.api.ErrorCode;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.util.Constants;
import org.example.finprocessor.util.LoggerUtil;
import org.example.finprocessor.util.ServerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.logging.Level;

@Component
public class StockMarketControllerFacade {
    private static final Logger logger = LoggerFactory.getLogger(StockMarketControllerFacade.class);

    private static final StringSerializer keySerializer = new StringSerializer();

    private static final Converter<StockPricePredictionDto, StockPricePredictionResponse>
        stockPricePredictionDtoToResponseConverter = new StockPricePredictionDtoToResponseConverter();

    private final StreamsBuilderFactoryBean factoryBean;

    private final FinanceProcessorClient financeProcessorClient;

    public StockMarketControllerFacade(
        StreamsBuilderFactoryBean factoryBean,
        FinanceProcessorClient financeProcessorClient
    ) {
        this.factoryBean = factoryBean;
        this.financeProcessorClient = financeProcessorClient;
    }

    private KafkaStreams getKafkaStreams() {
        return Objects.requireNonNull(factoryBean.getKafkaStreams());
    }

    private ReadOnlyKeyValueStore<String, StockPricePredictionDto> getStockPricePredictionLocalStore() {
        final var kafkaStreams = getKafkaStreams();
        return kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                Constants.PREDICTIONS_STORE,
                QueryableStoreTypes.keyValueStore()
            )
        );
    }

    private ReadOnlyKeyValueStore<String, SortedSet<TopPredictionResponse>> getTopPredictionsLocalStore() {
        final var kafkaStreams = getKafkaStreams();
        return kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                Constants.TOP_PREDICTIONS_STORE,
                QueryableStoreTypes.keyValueStore()
            )
        );
    }

    private static StockPricePredictionResponse toPricePredictionDto(StockPricePredictionDto value) {
        final var predictionResponse = stockPricePredictionDtoToResponseConverter.convert(value);
        return Objects.requireNonNull(predictionResponse);
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

    public Flux<StockPricePredictionResponse> getPredictions(
        ServerWebExchange exchange,
        GetPredictionsParams predictionParams
    ) {
        final var predictionsFromRemoteStoreFlux = getRemoteStoreHosts(
            exchange.getRequest(), Constants.PREDICTIONS_STORE
        )
            .flatMapIterable(urls -> urls)
            .flatMap(url -> financeProcessorClient.getPredictions(url, predictionParams));

        final var predictionsFromLocalStoreFlux = getPredictionsFromLocalStore(predictionParams);

        return Flux.merge(predictionsFromRemoteStoreFlux, predictionsFromLocalStoreFlux)
            .log("getPredictions", Level.INFO, true, SignalType.values())
            .timeout(AppConfig.DEFAULT_PUBLISHER_TIMEOUT);
    }

    public Flux<StockPricePredictionResponse> getPredictionsFromLocalStore(GetPredictionsParams predictionParams) {
        return Flux.<StockPricePredictionResponse>create(fluxSink -> {
                final var mode = predictionParams.mode();
                final var to = predictionParams.to();
                final var from = predictionParams.from();

                final var readOnlyKeyValueStore = getStockPricePredictionLocalStore();

                final KeyValueIterator<String, StockPricePredictionDto> iterator;

                switch (mode) {
                    case PREFIX_SCAN -> {
                        final var prefix = predictionParams.prefix();
                        iterator = readOnlyKeyValueStore.prefixScan(prefix, keySerializer);
                    }
                    case RANGE -> iterator = readOnlyKeyValueStore.range(from, to);
                    case REVERSE_RANGE -> iterator = readOnlyKeyValueStore.reverseRange(from, to);
                    case REVERSE_ALL -> iterator = readOnlyKeyValueStore.reverseAll();
                    default -> iterator = readOnlyKeyValueStore.all();
                }

                emitPredictions(iterator, fluxSink);
                fluxSink.complete();
            })
            .log("getPredictionsFromLocalStore", Level.INFO, true, SignalType.values());
    }

    private static void emitPredictions(
        KeyValueIterator<String, StockPricePredictionDto> iterator,
        FluxSink<StockPricePredictionResponse> fluxSink
    ) {
        try (iterator) {
            while (iterator.hasNext()) {
                final var next = iterator.next();
                final var value = next.value;
                final var pricePredictionDto = toPricePredictionDto(value);
                fluxSink.next(pricePredictionDto);
            }
        }
    }

    public Mono<StockPricePredictionResponse> getPredictionByTicker(
        ServerWebExchange exchange, String ticker
    ) {
        return Mono.<StockPricePredictionResponse>create(sink -> {
                final var metadataForKey = getKafkaStreams()
                    .queryMetadataForKey(Constants.PREDICTIONS_STORE, ticker, keySerializer);

                if (logger.isDebugEnabled()) {
                    logger.debug("Active host: {}", metadataForKey.activeHost());
                    logger.debug("Standby hosts: {}", metadataForKey.standbyHosts());
                }

                final var predictionResponseMono = findKeyHolderForSearchByTicker(
                    exchange, ticker, metadataForKey
                );
                predictionResponseMono
                    .materialize()
                    .subscribe(signal -> {
                        if (signal.isOnNext()) {
                            sink.success(signal.get());
                        } else {
                            if (signal.isOnError() && signal.hasError()) {
                                sink.error(Objects.requireNonNull(signal.getThrowable()));
                            } else {
                                sink.error(new EntityNotFoundException(ErrorCode.TICKER_NOT_FOUND));
                            }
                        }
                    });
            })
            .log("getPredictionByTicker", Level.INFO, true, SignalType.values())
            .timeout(AppConfig.DEFAULT_PUBLISHER_TIMEOUT);
    }

    private Mono<StockPricePredictionResponse> findKeyHolderForSearchByTicker(
        ServerWebExchange exchange, String ticker, KeyQueryMetadata keyQueryMetadata
    ) {
        var predictionResponseMono = Mono.<StockPricePredictionResponse>empty();

        final var activeHost = keyQueryMetadata.activeHost();
        if (ServerUtil.isActiveHost(exchange.getRequest(), activeHost)) {
            final var readOnlyKeyValueStore = getStockPricePredictionLocalStore();

            final var value = readOnlyKeyValueStore.get(ticker);
            if (value != null) {
                predictionResponseMono = Mono.just(toPricePredictionDto(value));
            }
        } else {
            final var hostUrl = String.format(
                Constants.REMOTE_HOST_FORMAT, activeHost.host(), activeHost.port()
            );
            predictionResponseMono = financeProcessorClient.getPredictionByTicker(hostUrl, ticker);
        }
        return predictionResponseMono;
    }

    public Flux<TopPredictionResponse> getTopPredictionsFromLocalStore() {
        return Mono.fromSupplier(() -> {
                final var topPredictionsLocalStore = getTopPredictionsLocalStore();
                final var key = Constants.TOP_PREDICTIONS;
                final var topPredictionSet = topPredictionsLocalStore.get(key);

                if (topPredictionSet != null) {
                    return Flux.fromIterable(topPredictionSet.reversed());
                } else {
                    return Flux.<TopPredictionResponse>empty();
                }
            })
            .flatMapMany(flux -> flux)
            .log("getTopPredictionsFromLocalStore", Level.INFO, true, SignalType.values())
            .timeout(AppConfig.DEFAULT_PUBLISHER_TIMEOUT);
    }

    public Flux<TopPredictionResponse> getTopPredictions(ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> {
                final var key = Constants.TOP_PREDICTIONS;
                final var metadataForKey = getKafkaStreams()
                    .queryMetadataForKey(Constants.TOP_PREDICTIONS_STORE, key, keySerializer);

                Flux<TopPredictionResponse> predictionResponseFlux;
                final var activeHost = metadataForKey.activeHost();
                if (ServerUtil.isActiveHost(exchange.getRequest(), activeHost)) {
                    predictionResponseFlux = getTopPredictionsFromLocalStore();
                } else {
                    final var hostUrl = String.format(
                        Constants.REMOTE_HOST_FORMAT, activeHost.host(), activeHost.port()
                    );
                    predictionResponseFlux = financeProcessorClient.getTopPredictions(hostUrl);
                }
                return predictionResponseFlux;
            })
            .flatMapMany(responseMono -> responseMono)
            .log("getTopPredictions", Level.INFO, true, SignalType.values())
            .timeout(AppConfig.DEFAULT_PUBLISHER_TIMEOUT);
    }

    private Flux<LossPredictionResponse> getLossPredictionsFromLocalStore(KeyValueIterator<String, String> iterator) {
        return Flux.create(fluxSink -> {
            try (iterator) {
                while (iterator.hasNext()) {
                    final var next = iterator.next();
                    fluxSink.next(
                        new LossPredictionResponse(next.key, new BigDecimal(next.value))
                    );
                }
            }
            fluxSink.complete();
        });
    }

    private ReadOnlyKeyValueStore<String, String> getLossPredictionsLocalStore() {
        final var kafkaStreams = getKafkaStreams();
        return kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(Constants.LOSS_PREDICTIONS_STORE, QueryableStoreTypes.keyValueStore())
        );
    }

    public Flux<LossPredictionResponse> getLossPredictionsFromLocalStore() {
        return Mono.fromSupplier(() -> {
                final var lossStore = getLossPredictionsLocalStore();
                final var iterator = lossStore.all();
                return getLossPredictionsFromLocalStore(iterator);
            })
            .flatMapMany(flux -> flux)
            .log("getLossPredictionsFromLocalStore", Level.INFO, true, SignalType.values())
            .timeout(AppConfig.DEFAULT_PUBLISHER_TIMEOUT);
    }

    public Flux<LossPredictionResponse> getLossPredictions(ServerWebExchange exchange) {
        final var predictionsFromRemoteStoreFlux = getRemoteStoreHosts(
            exchange.getRequest(), Constants.LOSS_PREDICTIONS_STORE
        )
            .flatMapIterable(urls -> urls)
            .flatMap(financeProcessorClient::getLossPredictions);

        final var predictionsFromLocalStoreFlux = getLossPredictionsFromLocalStore();

        return Flux.merge(predictionsFromRemoteStoreFlux, predictionsFromLocalStoreFlux)
            .log("getLossPredictions", Level.INFO, true, SignalType.values())
            .timeout(AppConfig.DEFAULT_PUBLISHER_TIMEOUT);
    }
}
