package org.example.finprocessor.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.component.NavigableSetJsonSerde;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.util.Constants;
import org.example.finprocessor.util.SerdeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.NavigableSet;

@Component
public class TopPredictionsListener implements StreamListener {
    private static final int SINGLE_PARTITION = 1;

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public TopPredictionsListener(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    @Autowired
    public void listen(StreamsBuilder streamsBuilder) {
        final var inputTopic = appProperties.stockMarketStream().outputTopic();
        final int limit = appProperties.topPredictionsStream().trackingLimit();

        final var predictionSerde = SerdeUtil.createPredictionSerde(objectMapper);
        final var consumed = Consumed.with(Serdes.String(), predictionSerde);

        final var topStoreNamed = Named.as(String.format("%s-%s", Constants.KSTREAM_PREFIX, Constants.TOP_PREDICTIONS));

        final var topPredictionsStoreBuilder = createStoreBuilder();
        streamsBuilder.addStateStore(topPredictionsStoreBuilder);

        final var topPredictionsProcessor = new TopPredictionsProcessor(topPredictionsStoreBuilder, limit);

        final var repartitioned = Repartitioned
            .<String, StockPricePredictionDto>as("TopPredictionsProcessor")
            .withNumberOfPartitions(SINGLE_PARTITION)
            .withValueSerde(predictionSerde);

        streamsBuilder.stream(inputTopic, consumed)
            .repartition(repartitioned)
            .selectKey((key, value) -> Constants.TOP_PREDICTIONS)
            .process(topPredictionsProcessor, topStoreNamed, Constants.TOP_PREDICTIONS_STORE);
    }

    private StoreBuilder<KeyValueStore<String, NavigableSet<TopPredictionResponse>>> createStoreBuilder() {
        return Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(Constants.TOP_PREDICTIONS_STORE),
            Serdes.String(),
            new NavigableSetJsonSerde<>(objectMapper, TopPredictionResponse.class)
        );
    }
}
