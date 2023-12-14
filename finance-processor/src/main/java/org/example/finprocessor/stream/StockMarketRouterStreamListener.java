package org.example.finprocessor.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.BranchedKStream;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.stockmarket.api.StockType;
import org.example.finprocessor.util.SerdeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class StockMarketRouterStreamListener implements StreamListener {
    private final AppProperties appProperties;

    private final ObjectMapper objectMapper;

    private final Map<StockType, Set<String>> stockTypeMap;

    public StockMarketRouterStreamListener(
        AppProperties appProperties,
        ObjectMapper objectMapper,
        Map<StockType, Set<String>> stockTypeMap
    ) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.stockTypeMap = stockTypeMap;
    }

    @Override
    @Autowired
    public void listen(StreamsBuilder streamsBuilder) {
        final var routerProps = appProperties.stockMarketRouterStream();
        final var inputTopic = routerProps.inputTopic();

        final var topicMap = collectStockTypesToMap();

        final var stockPriceSerde = SerdeUtil.createStockPriceSerde(objectMapper);

        final var keySerde = Serdes.String();
        final var branchedKStream = streamsBuilder
            .stream(inputTopic, Consumed.with(keySerde, stockPriceSerde))
            .split(Named.as(inputTopic));

        createStreamBranches(branchedKStream, topicMap);

        branchedKStream.defaultBranch(getDefaultBranch())
            .forEach((branchKey, kStream) -> kStream
                .map(this::setKeyIfAbsent)
                .to(branchKey, Produced.with(keySerde, stockPriceSerde))
            );
    }

    private Branched<String, StockPrice> getDefaultBranch() {
        final var routerProps = appProperties.stockMarketRouterStream();
        return Branched.as(routerProps.shareTopicSuffix());
    }

    private void createStreamBranches(
        BranchedKStream<String, StockPrice> branchedKStream,
        Map<StockType, String> topicMap
    ) {
        for (var entry : stockTypeMap.entrySet()) {
            branchedKStream.branch(
                (k, v) -> entry.getValue().contains(v.ticker()),
                Branched.as(topicMap.get(entry.getKey()))
            );
        }
    }

    private Map<StockType, String> collectStockTypesToMap() {
        final var finProcessorProps = appProperties.stockMarketRouterStream();
        return Map.of(
            StockType.ETF, finProcessorProps.etfTopicSuffix(),
            StockType.FUND, finProcessorProps.fundTopicSuffix()
        );
    }

    private KeyValue<String, StockPrice> setKeyIfAbsent(String key, StockPrice stockPrice) {
        if (key == null || key.isBlank()) {
            key = stockPrice.ticker();
        }
        return KeyValue.pair(key, stockPrice);
    }
}
