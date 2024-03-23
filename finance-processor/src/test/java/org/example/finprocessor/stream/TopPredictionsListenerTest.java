package org.example.finprocessor.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.config.AppConfig;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.config.TopPredictionsStreamProperties;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.test.AppPropertiesFactory;
import org.example.finprocessor.test.KafkaStreamsPropertiesFactory;
import org.example.finprocessor.test.StockPriceFactory;
import org.example.finprocessor.test.StockPricePredictionDtoFactory;
import org.example.finprocessor.util.Constants;
import org.example.finprocessor.util.SerdeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SameParameterValue")
class TopPredictionsListenerTest {
    private final KafkaStreamsPropertiesFactory kafkaStreamsPropertiesFactory = new KafkaStreamsPropertiesFactory();
    private AppPropertiesFactory appPropertiesFactory;
    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private TopPredictionsListener streamListener;
    private Properties kafkaStreamsProps;

    @BeforeEach
    void setUp() {
        appPropertiesFactory = new AppPropertiesFactory();
        appProperties = appPropertiesFactory.create();
        final var appConfig = new AppConfig(appProperties);
        objectMapper = appConfig.objectMapper();
        streamListener = new TopPredictionsListener(objectMapper, appProperties);
        kafkaStreamsProps = kafkaStreamsPropertiesFactory.create();
    }

    @Test
    void test_success() {
        // given
        final var vooETF = StockPriceFactory.vooAt20231212();
        final var vooPricePredictionDto = StockPricePredictionDtoFactory.of(
            vooETF, BigDecimal.valueOf(341.3360107421874)
        );

        final var googlShare = StockPriceFactory.googlAt20231212();
        final var googlPricePredictionDto = StockPricePredictionDtoFactory.of(
            googlShare, BigDecimal.valueOf(132.52000427246094)
        );

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

            // when
            inputTopic.pipeKeyValueList(List.of(
                new KeyValue<>(vooPricePredictionDto.ticker(), vooPricePredictionDto),
                new KeyValue<>(googlPricePredictionDto.ticker(), googlPricePredictionDto)
            ));

            // then
            final KeyValueStore<String, SortedSet<TopPredictionResponse>> store =
                topologyTestDriver.getKeyValueStore(Constants.TOP_PREDICTIONS_STORE);

            assertTrue(store.approximateNumEntries() > 0);

            final var topPredictions = store.get(Constants.TOP_PREDICTIONS);
            assertNotNull(topPredictions);
            assertEquals(2, topPredictions.size());

            assertTrue(topPredictions.contains(
                new TopPredictionResponse(vooPricePredictionDto.ticker(), vooPricePredictionDto.pricePrediction())
            ));
            assertTrue(topPredictions.contains(
                new TopPredictionResponse(googlPricePredictionDto.ticker(), googlPricePredictionDto.pricePrediction())
            ));
        }
    }

    @Test
    void test_success_with_existing_ticker() {
        // given
        final var vooETF = StockPriceFactory.vooAt20231212();
        final var vooPricePredictionDto = StockPricePredictionDtoFactory.of(
            vooETF, BigDecimal.valueOf(341.3360107421874)
        );
        final var vooPricePredictionDtoUpdate = StockPricePredictionDtoFactory.of(
            vooETF, BigDecimal.valueOf(341.34)
        );

        final var googlShare = StockPriceFactory.googlAt20231212();
        final var googlPricePredictionDto = StockPricePredictionDtoFactory.of(
            googlShare, BigDecimal.valueOf(132.52000427246094)
        );

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

            // when
            inputTopic.pipeKeyValueList(List.of(
                new KeyValue<>(vooPricePredictionDto.ticker(), vooPricePredictionDto),
                new KeyValue<>(googlPricePredictionDto.ticker(), googlPricePredictionDto),
                new KeyValue<>(vooPricePredictionDtoUpdate.ticker(), vooPricePredictionDtoUpdate)
            ));

            // then
            final KeyValueStore<String, SortedSet<TopPredictionResponse>> store =
                topologyTestDriver.getKeyValueStore(Constants.TOP_PREDICTIONS_STORE);

            assertTrue(store.approximateNumEntries() > 0);

            final var topPredictions = store.get(Constants.TOP_PREDICTIONS);
            assertNotNull(topPredictions);
            assertEquals(2, topPredictions.size());

            assertTrue(topPredictions.contains(
                new TopPredictionResponse(vooPricePredictionDtoUpdate.ticker(), vooPricePredictionDtoUpdate.pricePrediction())
            ));
            assertTrue(topPredictions.contains(
                new TopPredictionResponse(googlPricePredictionDto.ticker(), googlPricePredictionDto.pricePrediction())
            ));
        }
    }

    private void prepareInfrastructure(int limit) {
        appPropertiesFactory = new AppPropertiesFactory();
        appPropertiesFactory.setTopPredictionsStreamPropertiesSupplier(() ->
            new TopPredictionsStreamProperties(limit)
        );
        appProperties = appPropertiesFactory.create();
        final var appConfig = new AppConfig(appProperties);
        objectMapper = appConfig.objectMapper();
        streamListener = new TopPredictionsListener(objectMapper, appProperties);
        kafkaStreamsProps = kafkaStreamsPropertiesFactory.create();
    }

    private List<KeyValue<String, StockPricePredictionDto>> createInputs() {
        final var vooETF = StockPriceFactory.vooAt20231212();
        final var vooPricePredictionDto = StockPricePredictionDtoFactory.of(
            vooETF, BigDecimal.valueOf(341.3360107421874)
        );

        final var googlShare = StockPriceFactory.googlAt20231212();
        final var googlPricePredictionDto = StockPricePredictionDtoFactory.of(
            googlShare, BigDecimal.valueOf(132.52000427246094)
        );

        final var spyETF = StockPriceFactory.spyAt20231212();
        final var spyPricePredictionDto = StockPricePredictionDtoFactory.of(
            spyETF, BigDecimal.valueOf(464.201)
        );

        final var vtsaxFund = StockPriceFactory.vtsaxAt20231212();
        final var vtsaxPricePredictionDto = StockPricePredictionDtoFactory.of(
            vtsaxFund, BigDecimal.valueOf(112.299)
        );

        final var nvdaShare = StockPriceFactory.nvdaAt20240221();
        final var nvdaPricePredictionDto = StockPricePredictionDtoFactory.of(
            nvdaShare, BigDecimal.valueOf(689.51)
        );

        final var aaplShare = StockPriceFactory.aaplAt20240221();
        final var aaplPricePredictionDto = StockPricePredictionDtoFactory.of(
            aaplShare, BigDecimal.valueOf(183.286)
        );

        final var amznShare = StockPriceFactory.amznAt20240221();
        final var amznPricePredictionDto = StockPricePredictionDtoFactory.of(
            amznShare, BigDecimal.valueOf(170.42)
        );

        final var maShare = StockPriceFactory.maAt20240221();
        final var maPricePredictionDto = StockPricePredictionDtoFactory.of(
            maShare, BigDecimal.valueOf(457.84)
        );

        final var msftShare = StockPriceFactory.msftAt20240221();
        final var msftPricePredictionDto = StockPricePredictionDtoFactory.of(
            msftShare, BigDecimal.valueOf(399.57)
        );

        final var tslaShare = StockPriceFactory.tslaAt20240221();
        final var tslaPricePredictionDto = StockPricePredictionDtoFactory.of(
            tslaShare, BigDecimal.valueOf(192.54)
        );

        return List.of(
            new KeyValue<>(vooPricePredictionDto.ticker(), vooPricePredictionDto),
            new KeyValue<>(googlPricePredictionDto.ticker(), googlPricePredictionDto),
            new KeyValue<>(spyPricePredictionDto.ticker(), spyPricePredictionDto),
            new KeyValue<>(vtsaxPricePredictionDto.ticker(), vtsaxPricePredictionDto),
            new KeyValue<>(nvdaPricePredictionDto.ticker(), nvdaPricePredictionDto),
            new KeyValue<>(aaplPricePredictionDto.ticker(), aaplPricePredictionDto),
            new KeyValue<>(amznPricePredictionDto.ticker(), amznPricePredictionDto),
            new KeyValue<>(maPricePredictionDto.ticker(), maPricePredictionDto),
            new KeyValue<>(msftPricePredictionDto.ticker(), msftPricePredictionDto),
            new KeyValue<>(tslaPricePredictionDto.ticker(), tslaPricePredictionDto)
        );
    }

    private static List<KeyValue<String, StockPricePredictionDto>> createExpectedItems(
        List<KeyValue<String, StockPricePredictionDto>> keyValueInputs,
        int limit
    ) {
        List<KeyValue<String, StockPricePredictionDto>> expectedItems = new ArrayList<>(keyValueInputs);
        final Comparator<KeyValue<String, StockPricePredictionDto>> pricePredictionComparator =
            Comparator.comparingDouble(value -> value.value.pricePrediction().doubleValue());
        expectedItems.sort(pricePredictionComparator);
        expectedItems = expectedItems.reversed().subList(0, limit);
        return expectedItems;
    }

    @Test
    void test_limit() {
        // given
        final int limit = 5;

        prepareInfrastructure(limit);

        final var keyValueInputs = createInputs();
        final var expectedItems = createExpectedItems(keyValueInputs, limit);

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

            // when
            inputTopic.pipeKeyValueList(keyValueInputs);

            // then
            final KeyValueStore<String, SortedSet<TopPredictionResponse>> store =
                topologyTestDriver.getKeyValueStore(Constants.TOP_PREDICTIONS_STORE);

            assertTrue(store.approximateNumEntries() > 0);

            final var topPredictions = store.get(Constants.TOP_PREDICTIONS)
                .reversed();
            assertNotNull(topPredictions);
            assertEquals(limit, topPredictions.size());

            expectedItems.forEach(expectedItem -> {
                final var topPredictionResponse = new TopPredictionResponse(expectedItem.key, expectedItem.value.pricePrediction());
                assertTrue(topPredictions.contains(
                    topPredictionResponse
                ), topPredictionResponse::toString);
            });
        }
    }
}
