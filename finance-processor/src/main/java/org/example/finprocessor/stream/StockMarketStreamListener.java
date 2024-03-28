package org.example.finprocessor.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
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

    @Override
    @Autowired
    public void listen(StreamsBuilder streamsBuilder) {
        final var streamProperties = appProperties.stockMarketStream();
        final var inputTopics = streamProperties.inputTopics();

        final var stockPriceSerde = SerdeUtil.createStockPriceSerde(objectMapper);

        final var keySerde = Serdes.String();
        @SuppressWarnings("unchecked") final Serde<List<StockPrice>> listSerde =
            Serdes.ListSerde(ArrayList.class, stockPriceSerde);

        final var stockPriceKGroupedStream = streamsBuilder
            .stream(inputTopics, Consumed.with(keySerde, stockPriceSerde))
            .groupByKey(Grouped.with(keySerde, stockPriceSerde));

        final KTable<Windowed<String>, List<StockPrice>> windowedAggregateKTable =
            new WindowStrategy(streamProperties, listSerde).apply(stockPriceKGroupedStream);

        final var predictionDtoKStream = windowedAggregateKTable
            .suppress(suppressStrategy(streamProperties))
            .mapValues(this::predictionMapper)
            .toStream()
            .filter((key, value) -> value != null)
            .selectKey((key, value) -> key.key());

        final var predictionSerde = SerdeUtil.createPredictionSerde(objectMapper);
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

    private StockPricePredictionDto predictionMapper(Windowed<String> readOnlyKey, List<StockPrice> prices) {
        final var highPrices = prices.stream()
            .map(StockPrice::high)
            .toList();

        final var sortedByDatePrices = prices.stream()
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
