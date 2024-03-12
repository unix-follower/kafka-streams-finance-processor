package org.example.finprocessor.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Aggregator;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Merger;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.ValueMapperWithKey;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueStore;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.config.StockMarketStreamProperties;
import org.example.finprocessor.predictor.api.FinancePredictionRequest;
import org.example.finprocessor.predictor.api.FinancePredictorClient;
import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.util.Constants;
import org.example.finprocessor.util.DateUtil;
import org.example.finprocessor.util.SerdeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class StockMarketStreamListener implements StreamListener {
    private final AppProperties appProperties;

    private final FinancePredictorClient finPredictorClient;

    private final ObjectMapper objectMapper;

    public StockMarketStreamListener(AppProperties appProperties, FinancePredictorClient finPredictorClient, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.finPredictorClient = finPredictorClient;
        this.objectMapper = objectMapper;
    }

    record WindowStrategy(
        StockMarketStreamProperties streamProperties,
        Serde<String> keySerde,
        Serde<List<StockPrice>> valueSerde
    ) {
        KTable<Windowed<String>, List<StockPrice>> apply(KGroupedStream<String, StockPrice> groupedStream) {
            final var windowType = streamProperties.windowType();

            final KTable<Windowed<String>, List<StockPrice>> windowedAggregateKTable;
            switch (windowType) {
                case TIME -> windowedAggregateKTable = applyTimeWindow(groupedStream);
                case SESSION -> windowedAggregateKTable = applySessionWindow(groupedStream);
                case SLIDING -> windowedAggregateKTable = applySlidingWindow(groupedStream);

                default -> throw new IllegalStateException("Unexpected value: " + windowType);
            }

            return windowedAggregateKTable;
        }

        private TimeWindows tumblingWindow(Duration windowSize) {
            return TimeWindows.ofSizeWithNoGrace(windowSize);
        }

        private TimeWindows hoppingWindow(Duration windowSize, Duration advance) {
            return TimeWindows.ofSizeAndGrace(windowSize, advance);
        }

        private KTable<Windowed<String>, List<StockPrice>> applyTimeWindow(
            KGroupedStream<String, StockPrice> groupedStream
        ) {
            final var window = streamProperties.window();
            final var gracePeriod = streamProperties.windowGracePeriod();

            final TimeWindows timeWindows;
            if (gracePeriod != null && gracePeriod.isPositive()) {
                timeWindows = hoppingWindow(window, gracePeriod);
            } else {
                timeWindows = tumblingWindow(window);
            }

            return groupedStream
                .windowedBy(timeWindows)
                .aggregate(
                    ArrayList::new,
                    stockPriceListAggregator(),
                    Materialized.with(keySerde, valueSerde)
                );
        }

        private KTable<Windowed<String>, List<StockPrice>> applySessionWindow(
            KGroupedStream<String, StockPrice> groupedStream
        ) {
            final var window = streamProperties.window();
            final var gracePeriod = streamProperties.windowGracePeriod();

            final SessionWindows sessionWindows;
            if (gracePeriod != null && gracePeriod.isPositive()) {
                sessionWindows = SessionWindows.ofInactivityGapAndGrace(window, gracePeriod);
            } else {
                sessionWindows = SessionWindows.ofInactivityGapWithNoGrace(window);
            }

            return groupedStream
                .windowedBy(sessionWindows)
                .aggregate(
                    ArrayList::new,
                    stockPriceListAggregator(),
                    stockPriceSessionMerger(),
                    Materialized.with(keySerde, valueSerde)
                );
        }

        private KTable<Windowed<String>, List<StockPrice>> applySlidingWindow(
            KGroupedStream<String, StockPrice> groupedStream
        ) {
            final var window = streamProperties.window();
            final var gracePeriod = streamProperties.windowGracePeriod();

            final SlidingWindows slidingWindows;
            if (gracePeriod != null && gracePeriod.isPositive()) {
                slidingWindows = SlidingWindows.ofTimeDifferenceAndGrace(window, gracePeriod);
            } else {
                slidingWindows = SlidingWindows.ofTimeDifferenceWithNoGrace(window);
            }

            return groupedStream
                .windowedBy(slidingWindows)
                .aggregate(
                    ArrayList::new,
                    stockPriceListAggregator(),
                    Materialized.with(keySerde, valueSerde)
                );
        }
    }

    @Override
    @Autowired
    public void listen(StreamsBuilder streamsBuilder) {
        final var streamProperties = appProperties.stockMarketStream();
        final var inputTopics = streamProperties.inputTopics();

        final var stockPriceSerde = SerdeUtil.createStockPriceSerde(objectMapper);

        final var keySerde = Serdes.String();
        @SuppressWarnings("unchecked") final Serde<List<StockPrice>> listSerde = Serdes.ListSerde(ArrayList.class, stockPriceSerde);

        final KGroupedStream<String, StockPrice> stockPriceKGroupedStream = streamsBuilder
            .stream(inputTopics, Consumed.with(keySerde, stockPriceSerde))
            .groupByKey(Grouped.with(keySerde, stockPriceSerde));

        final KTable<Windowed<String>, List<StockPrice>> windowedAggregateKTable =
            new WindowStrategy(streamProperties, keySerde, listSerde).apply(stockPriceKGroupedStream);

        final var suppressed = suppressStrategy(streamProperties);

        final KTable<Windowed<String>, StockPricePredictionDto> windowedPredictionKTable = windowedAggregateKTable
            .suppress(suppressed)
            .mapValues(predictionMapper());

        final var predictionSerde = SerdeUtil.createPredictionSerde(objectMapper);

        final var predictionDtoKStream = windowedPredictionKTable.toStream()
            .filter((key, value) -> value != null)
            .selectKey((key, value) -> key.key());

        predictionDtoKStream.toTable(
            Named.as(Constants.PREDICTIONS_STORE),
            Materialized.<String, StockPricePredictionDto, KeyValueStore<Bytes, byte[]>>as(Constants.PREDICTIONS_STORE)
                .withKeySerde(keySerde)
                .withValueSerde(predictionSerde)
        );

        final var outputTopic = streamProperties.outputTopic();
        predictionDtoKStream.to(outputTopic, Produced.with(keySerde, predictionSerde));
    }

    @SuppressWarnings("rawtypes")
    private static Suppressed<Windowed> suppressStrategy(StockMarketStreamProperties streamProperties) {
        final var suppressType = streamProperties.windowSuppressType();

        final Suppressed<Windowed> suppressed;
        if (suppressType == WindowSuppressType.TIME_LIMIT) {
            suppressed = Suppressed.untilTimeLimit(
                streamProperties.windowSuppressAwaitMoreEvents(),
                Suppressed.BufferConfig.maxRecords(streamProperties.windowSuppressMaxRecords())
            );
        } else {
            suppressed = Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded());
        }

        return suppressed;
    }

    private static Aggregator<String, StockPrice, List<StockPrice>> stockPriceListAggregator() {
        return (key, value, aggregate) -> {
            aggregate.add(value);
            return aggregate;
        };
    }

    private static Merger<String, List<StockPrice>> stockPriceSessionMerger() {
        return (aggKey, aggOne, aggTwo) -> {
            aggOne.addAll(aggTwo);
            return aggOne;
        };
    }

    private ValueMapperWithKey<Windowed<String>, List<StockPrice>, StockPricePredictionDto> predictionMapper() {
        return this::makePrediction;
    }

    private StockPricePredictionDto makePrediction(Windowed<String> readOnlyKey, List<StockPrice> value) {
        final var highPrices = value.stream()
            .map(StockPrice::high)
            .toList();

        final var sortedByDatePrices = value.stream()
            .sorted(Comparator.comparing(StockPrice::date))
            .toList();

        final var openRangeAt = DateUtil.estToUtc(sortedByDatePrices.getFirst().date());
        final var lastStockPriceItem = sortedByDatePrices.getLast();
        final var closedRangeAt = DateUtil.estToUtc(lastStockPriceItem.date());

        final var predictionRequest = new FinancePredictionRequest(
            readOnlyKey.key(),
            openRangeAt,
            closedRangeAt,
            highPrices
        );
        final var financePredictionResponse = finPredictorClient.predict(predictionRequest)
            .toFuture().join();

        return new StockPricePredictionDto(
            readOnlyKey.key(),
            openRangeAt,
            lastStockPriceItem.close(),
            closedRangeAt,
            OffsetDateTime.now(ZoneOffset.UTC),
            financePredictionResponse.prediction()
        );
    }
}
