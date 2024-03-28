package org.example.finprocessor.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.example.finprocessor.config.AppConfig;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.predictor.api.FinancePredictionResponse;
import org.example.finprocessor.predictor.api.FinancePredictorClient;
import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.test.AppPropertiesFactory;
import org.example.finprocessor.test.KafkaStreamsPropertiesFactory;
import org.example.finprocessor.test.StockPriceFactory;
import org.example.finprocessor.test.StockPricePredictionDtoFactory;
import org.example.finprocessor.test.StockPricePredictionDtoUtil;
import org.example.finprocessor.util.Constants;
import org.example.finprocessor.util.SerdeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Properties;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class StockMarketStreamListenerTest {
    private static final BigDecimal PRICE_PREDICTION = BigDecimal.valueOf(427);
    private final KafkaStreamsPropertiesFactory kafkaStreamsPropertiesFactory = new KafkaStreamsPropertiesFactory();
    private AppPropertiesFactory appPropertiesFactory;
    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private StockMarketStreamListener streamListener;
    private Properties kafkaStreamsProps;
    @Mock
    private FinancePredictorClient financePredictorClientMock;

    @BeforeEach
    void setUp() {
        appPropertiesFactory = new AppPropertiesFactory();
    }

    private void prepareListener() {
        appProperties = appPropertiesFactory.create();
        final var appConfig = new AppConfig(appProperties);
        objectMapper = appConfig.objectMapper();
        streamListener = new StockMarketStreamListener(appProperties, financePredictorClientMock, objectMapper);

        kafkaStreamsProps = kafkaStreamsPropertiesFactory.create();
    }

    @Test
    void test_success() {
        prepareListener();
        executeSuccessfulProcessing(state ->
            assertWithNoOverlap(state.predictionsStore(), state.outputTopic(), state.stockPrice())
        );
    }

    private void executeSuccessfulProcessing(Consumer<CheckPricePredictionState> executeChecks) {
        // given
        Mockito.when(financePredictorClientMock.predict(Mockito.any()))
            .thenReturn(Mono.just(new FinancePredictionResponse(PRICE_PREDICTION)));

        final var streamProps = appProperties.stockMarketStream();
        final var inputTopicNames = streamProps.inputTopics();

        final var streamsBuilder = new StreamsBuilder();
        streamListener.listen(streamsBuilder);

        final var keySerde = Serdes.String();
        final var stockPriceSerde = SerdeUtil.createStockPriceSerde(objectMapper);
        final var predictionSerde = SerdeUtil.createPredictionSerde(objectMapper);

        final var topologyTestDriver = new TopologyTestDriver(streamsBuilder.build(), kafkaStreamsProps);
        try (topologyTestDriver; keySerde; stockPriceSerde; predictionSerde) {
            final var inputTopic = topologyTestDriver.createInputTopic(
                inputTopicNames.stream().findFirst().orElseThrow(),
                keySerde.serializer(),
                stockPriceSerde.serializer()
            );

            final var outputTopic = topologyTestDriver.createOutputTopic(
                streamProps.outputTopic(),
                keySerde.deserializer(),
                predictionSerde.deserializer()
            );

            final var vooETF = StockPriceFactory.vooAt20231212();
            // when
            final var now = Instant.now();
            inputTopic.pipeInput(vooETF.ticker(), vooETF, now);
            final var windowCloseTimestamp = now.plus(streamProps.window())
                .plusSeconds(1);
            inputTopic.pipeInput(vooETF.ticker(), vooETF, windowCloseTimestamp);

            // then
            final KeyValueStore<String, StockPricePredictionDto> predictionsStore =
                topologyTestDriver.getKeyValueStore(Constants.PREDICTIONS_STORE);

            executeChecks.accept(new CheckPricePredictionState(predictionsStore, outputTopic, vooETF, 1));
        }
    }

    private record CheckPricePredictionState(
        KeyValueStore<String, StockPricePredictionDto> predictionsStore,
        TestOutputTopic<String, StockPricePredictionDto> outputTopic,
        StockPrice stockPrice,
        int recordCount
    ) {}

    private static void checkPricePrediction(
        KeyValueStore<String, StockPricePredictionDto> predictionsStore,
        TestOutputTopic<String, StockPricePredictionDto> outputTopic,
        StockPrice stockPrice,
        int recordCount
    ) {
        assertEquals(recordCount, predictionsStore.approximateNumEntries());

        final var predictionDto = predictionsStore.get("VOO");
        assertNotNull(predictionDto);
        assertEquals("VOO", predictionDto.ticker());
        assertEquals(PRICE_PREDICTION, predictionDto.pricePrediction());

        final var outputList = outputTopic.readKeyValuesToList();
        assertEquals(recordCount, outputList.size());
        final var firstOutputKeyValue = outputList.getFirst();
        assertEquals("VOO", firstOutputKeyValue.key);

        final var pricePredictionDto = StockPricePredictionDtoFactory.of(
            stockPrice, PRICE_PREDICTION
        );
        assertTrue(StockPricePredictionDtoUtil.equalsWithPredictionAtGtOrEq(
            pricePredictionDto, firstOutputKeyValue.value
        ));
    }

    private static void assertWithNoOverlap(
        KeyValueStore<String, StockPricePredictionDto> predictionsStore,
        TestOutputTopic<String, StockPricePredictionDto> outputTopic,
        StockPrice stockPrice
    ) {
        checkPricePrediction(predictionsStore, outputTopic, stockPrice, 1);
    }

    private static void assertWithOverlap(
        KeyValueStore<String, StockPricePredictionDto> predictionsStore,
        TestOutputTopic<String, StockPricePredictionDto> outputTopic,
        StockPrice stockPrice
    ) {
        checkPricePrediction(predictionsStore, outputTopic, stockPrice, 2);
    }

    @Test
    void test_success_with_time_window() {
        // given
        appPropertiesFactory.setStockMarketStreamPropertiesSupplier(
            AppPropertiesFactory.PROPS_SUPPLIER_WITH_TIME_WINDOW_MODE
        );
        prepareListener();

        executeSuccessfulProcessing(state ->
            assertWithNoOverlap(state.predictionsStore(), state.outputTopic(), state.stockPrice())
        );
    }

    @Test
    void test_success_with_sliding_window() {
        // given
        appPropertiesFactory.setStockMarketStreamPropertiesSupplier(
            AppPropertiesFactory.PROPS_SUPPLIER_WITH_SLIDING_WINDOW_MODE
        );
        prepareListener();

        executeSuccessfulProcessing(state ->
            assertWithOverlap(state.predictionsStore(), state.outputTopic(), state.stockPrice())
        );
    }
}
