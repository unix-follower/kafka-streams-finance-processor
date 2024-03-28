package org.example.finprocessor.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.example.finprocessor.config.AppConfig;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.test.AppPropertiesFactory;
import org.example.finprocessor.test.KafkaStreamsPropertiesFactory;
import org.example.finprocessor.test.StockPriceFactory;
import org.example.finprocessor.test.StockPricePredictionDtoFactory;
import org.example.finprocessor.util.Constants;
import org.example.finprocessor.util.SerdeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Properties;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LossPredictionsListenerTest {
    private static final BigDecimal PREDICTION_EQUAL_TO_CLOSE = BigDecimal.valueOf(426.6700134277344);

    // 20% of 426.6700134277344 = 85.334002685547
    // 426.6700134277344 - 85.334002685547 = 341.3360107421874
    private static final BigDecimal PREDICTION_20PERCENT_LOSS = BigDecimal.valueOf(341.3360107421874);

    // 50% of 426.6700134277344 = 213.33500671387
    // 426.6700134277344 - 213.33500671387 = 213.3350067138644
    private static final BigDecimal PREDICTION_50PERCENT_LOSS = BigDecimal.valueOf(213.3350067138644);

    private final AppPropertiesFactory appPropertiesFactory = new AppPropertiesFactory();
    private final KafkaStreamsPropertiesFactory kafkaStreamsPropertiesFactory = new KafkaStreamsPropertiesFactory();
    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private LossPredictionsListener streamListener;
    private Properties kafkaStreamsProps;

    @BeforeEach
    void setUp() {
        appProperties = appPropertiesFactory.create();
        final var appConfig = new AppConfig(appProperties);
        objectMapper = appConfig.objectMapper();
        streamListener = new LossPredictionsListener(objectMapper, appProperties);
        kafkaStreamsProps = kafkaStreamsPropertiesFactory.create();
    }

    private void test_loss_listener(
        BigDecimal pricePrediction,
        Consumer<KeyValueStore<String, String>> lossStoreConsumer
    ) {
        // given
        final var streamsBuilder = new StreamsBuilder();
        streamListener.listen(streamsBuilder);

        final var keySerde = Serdes.String();
        final var valueSerde = SerdeUtil.createPredictionSerde(objectMapper);

        final var topologyTestDriver = new TopologyTestDriver(streamsBuilder.build(), kafkaStreamsProps);
        try (topologyTestDriver; keySerde; valueSerde) {
            final var inputTopic = topologyTestDriver.createInputTopic(
                appProperties.stockMarketStream().outputTopic(),
                keySerde.serializer(),
                valueSerde.serializer()
            );

            final var vooETF = StockPriceFactory.vooAt20231212();
            final var pricePredictionDto = StockPricePredictionDtoFactory.of(
                vooETF, pricePrediction
            );

            // when
            inputTopic.pipeInput(pricePredictionDto.ticker(), pricePredictionDto);

            // then
            final KeyValueStore<String, String> lossStore =
                topologyTestDriver.getKeyValueStore(Constants.LOSS_PREDICTIONS_STORE);
            lossStoreConsumer.accept(lossStore);
        }
    }

    @Test
    void test_no_loss() {
        test_loss_listener(
            PREDICTION_EQUAL_TO_CLOSE,
            store -> assertEquals(0, store.approximateNumEntries())
        );
    }

    @Test
    void test_20_percent_loss() {
        final Consumer<KeyValueStore<String, String>> lossStoreConsumer = store -> {
            assertEquals(1, store.approximateNumEntries());

            final var fallenPrice = store.get("VOO");
            assertEquals(PREDICTION_20PERCENT_LOSS.toPlainString(), fallenPrice);
        };
        test_loss_listener(PREDICTION_20PERCENT_LOSS, lossStoreConsumer);
    }

    @Test
    void test_50_percent_loss() {
        final Consumer<KeyValueStore<String, String>> lossStoreConsumer = store -> {
            assertEquals(1, store.approximateNumEntries());

            final var fallenPrice = store.get("VOO");
            assertEquals(PREDICTION_50PERCENT_LOSS.toPlainString(), fallenPrice);
        };
        test_loss_listener(PREDICTION_50PERCENT_LOSS, lossStoreConsumer);
    }
}
