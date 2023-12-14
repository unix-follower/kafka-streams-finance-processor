package org.example.finprocessor.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.TestRecord;
import org.example.finprocessor.config.AppConfig;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.stream.StockMarketRouterStreamListener;
import org.example.finprocessor.test.AppPropertiesFactory;
import org.example.finprocessor.test.KafkaStreamsPropertiesFactory;
import org.example.finprocessor.test.StockPriceFactory;
import org.example.finprocessor.util.SerdeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockMarketRouterStreamListenerTest {
    private static final int ARRAY_2ND_INDEX = 1;

    private final AppPropertiesFactory appPropertiesFactory = new AppPropertiesFactory();
    private final KafkaStreamsPropertiesFactory kafkaStreamsPropertiesFactory = new KafkaStreamsPropertiesFactory();
    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private StockMarketRouterStreamListener streamListener;
    private Properties kafkaStreamsProps;


    @BeforeEach
    void setUp() {
        appProperties = appPropertiesFactory.create();
        final var appConfig = new AppConfig(appProperties);
        objectMapper = appConfig.objectMapper();
        final var stockTypeMap = appConfig.stockTypeMap();
        streamListener = new StockMarketRouterStreamListener(appProperties, objectMapper, stockTypeMap);

        kafkaStreamsProps = kafkaStreamsPropertiesFactory.create();
    }

    @Test
    void test_router() {
        // given
        final var inputTopicName = appProperties.stockMarketRouterStream().inputTopic();
        final var etfTopic = appProperties.stockMarketRouterStream().getETFTopicName();
        final var fundTopic = appProperties.stockMarketRouterStream().getFundTopicName();
        final var shareTopic = appProperties.stockMarketRouterStream().getShareTopicName();

        final var streamsBuilder = new StreamsBuilder();
        streamListener.listen(streamsBuilder);

        final var keySerde = Serdes.String();
        final var stockPriceSerde = SerdeUtil.createStockPriceSerde(objectMapper);

        final var topologyTestDriver = new TopologyTestDriver(streamsBuilder.build(), kafkaStreamsProps);
        try (topologyTestDriver; keySerde; stockPriceSerde) {
            final var inputTopic = topologyTestDriver.createInputTopic(
                inputTopicName,
                keySerde.serializer(),
                stockPriceSerde.serializer()
            );

            final var etfOutputTopic = topologyTestDriver.createOutputTopic(
                etfTopic,
                keySerde.deserializer(),
                stockPriceSerde.deserializer()
            );

            final var fundOutputTopic = topologyTestDriver.createOutputTopic(
                fundTopic,
                keySerde.deserializer(),
                stockPriceSerde.deserializer()
            );

            final var shareOutputTopic = topologyTestDriver.createOutputTopic(
                shareTopic,
                keySerde.deserializer(),
                stockPriceSerde.deserializer()
            );

            final var vooETF = StockPriceFactory.vooAt20231212();
            final var spyETF = StockPriceFactory.spyAt20231212();
            final var googlShare = StockPriceFactory.googlAt20231212();
            final var vtsaxFund = StockPriceFactory.vtsaxAt20231212();
            // when
            inputTopic.pipeValueList(List.of(
                vooETF,
                spyETF,
                googlShare,
                vtsaxFund
            ));

            // then
            final var etfKeyValueList = etfOutputTopic.readRecordsToList();
            checkFirstRecord(etfKeyValueList, 2, vooETF);

            final var secondETFRecord = etfKeyValueList.get(ARRAY_2ND_INDEX);
            assertEquals("SPY", secondETFRecord.key());
            assertEquals(spyETF, secondETFRecord.value());

            final var shareKeyValueList = shareOutputTopic.readRecordsToList();
            checkFirstRecord(shareKeyValueList, 1, googlShare);

            final var fundKeyValueList = fundOutputTopic.readRecordsToList();
            checkFirstRecord(fundKeyValueList, 1, vtsaxFund);
        }
    }

    private void checkFirstRecord(
        List<TestRecord<String, StockPrice>> keyValueList,
        int expectedSize,
        StockPrice expectedStockPrice
    ) {
        assertEquals(expectedSize, keyValueList.size());

        final var firstRecord = keyValueList.getFirst();
        assertEquals(expectedStockPrice.ticker(), firstRecord.key());
        assertEquals(expectedStockPrice, firstRecord.value());
    }

    private void testRecordKey(String key) {
        // given
        final var inputTopicName = appProperties.stockMarketRouterStream().inputTopic();
        final var etfTopic = appProperties.stockMarketRouterStream().getETFTopicName();

        final var streamsBuilder = new StreamsBuilder();
        streamListener.listen(streamsBuilder);

        final var keySerde = Serdes.String();
        final var stockPriceSerde = SerdeUtil.createStockPriceSerde(objectMapper);

        final var topologyTestDriver = new TopologyTestDriver(streamsBuilder.build(), kafkaStreamsProps);
        try (topologyTestDriver; keySerde; stockPriceSerde) {
            final var inputTopic = topologyTestDriver.createInputTopic(
                inputTopicName,
                keySerde.serializer(),
                stockPriceSerde.serializer()
            );

            final var etfOutputTopic = topologyTestDriver.createOutputTopic(
                etfTopic,
                keySerde.deserializer(),
                stockPriceSerde.deserializer()
            );

            final var vooETF = StockPriceFactory.vooAt20231212();
            // when
            inputTopic.pipeKeyValueList(List.of(
                KeyValue.pair(key, vooETF)
            ));

            // then
            final var etfKeyValueList = etfOutputTopic.readRecordsToList();
            checkFirstRecord(etfKeyValueList, 1, vooETF);
        }
    }

    @Test
    void test_router_with_null_key_input() {
        testRecordKey(null);
    }

    @Test
    void test_router_with_empty_key_input() {
        testRecordKey("");
    }

    @Test
    void test_router_with_blank_key_input() {
        testRecordKey(" ");
    }

    @Test
    void test_router_with_VOO_key_input() {
        testRecordKey("VOO");
    }
}
