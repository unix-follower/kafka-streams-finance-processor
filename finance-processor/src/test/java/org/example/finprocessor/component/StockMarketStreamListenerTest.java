package org.example.finprocessor.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.example.finprocessor.config.AppConfig;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.predictor.api.FinancePredictionResponse;
import org.example.finprocessor.predictor.api.FinancePredictorClient;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.stream.StockMarketStreamListener;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class StockMarketStreamListenerTest {
    private static final BigDecimal PRICE_PREDICTION = BigDecimal.valueOf(427);
    private final AppPropertiesFactory appPropertiesFactory = new AppPropertiesFactory();
    private final KafkaStreamsPropertiesFactory kafkaStreamsPropertiesFactory = new KafkaStreamsPropertiesFactory();
    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private StockMarketStreamListener streamListener;
    private Properties kafkaStreamsProps;
    @Mock
    private FinancePredictorClient financePredictorClient;

    @BeforeEach
    void setUp() {
        appProperties = appPropertiesFactory.create();
        final var appConfig = new AppConfig(appProperties);
        objectMapper = appConfig.objectMapper();
        streamListener = new StockMarketStreamListener(appProperties, financePredictorClient, objectMapper);

        kafkaStreamsProps = kafkaStreamsPropertiesFactory.create();
    }

    @Test
    void test_success() {
        // given
        Mockito.when(financePredictorClient.predict(Mockito.any()))
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

            assertEquals(1, predictionsStore.approximateNumEntries());

            final var predictionDto = predictionsStore.get("VOO");
            assertNotNull(predictionDto);
            assertEquals("VOO", predictionDto.ticker());
            assertEquals(PRICE_PREDICTION, predictionDto.pricePrediction());

            final var outputList = outputTopic.readKeyValuesToList();
            assertEquals(1, outputList.size());
            final var firstOutputKeyValue = outputList.getFirst();
            assertEquals("VOO", firstOutputKeyValue.key);

            final var pricePredictionDto = StockPricePredictionDtoFactory.of(
                vooETF, PRICE_PREDICTION
            );
            assertTrue(StockPricePredictionDtoUtil.equalsWithPredictionAtGtOrEq(
                pricePredictionDto, firstOutputKeyValue.value
            ));
        }
    }
}
