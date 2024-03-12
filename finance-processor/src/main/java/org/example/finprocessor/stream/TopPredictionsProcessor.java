package org.example.finprocessor.stream;

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class TopPredictionsProcessor implements ProcessorSupplier<
    String, StockPricePredictionDto, String, NavigableSet<TopPredictionResponse>
    > {
    private final StoreBuilder<KeyValueStore<String, NavigableSet<TopPredictionResponse>>> topPredictionsStoreBuilder;

    private final int limit;

    public TopPredictionsProcessor(
        StoreBuilder<KeyValueStore<String, NavigableSet<TopPredictionResponse>>> topPredictionsStoreBuilder, int limit
    ) {
        this.limit = limit;
        Objects.requireNonNull(topPredictionsStoreBuilder);
        this.topPredictionsStoreBuilder = topPredictionsStoreBuilder;
    }

    @Override
    public Set<StoreBuilder<?>> stores() {
        return Set.of(topPredictionsStoreBuilder);
    }

    @Override
    public Processor<String, StockPricePredictionDto, String, NavigableSet<TopPredictionResponse>> get() {
        return new Processor<>() {
            KeyValueStore<String, NavigableSet<TopPredictionResponse>> predictionsStore;

            @Override
            public void init(ProcessorContext<String, NavigableSet<TopPredictionResponse>> context) {
                predictionsStore = context.getStateStore(topPredictionsStoreBuilder.name());
            }

            @Override
            public void process(Record<String, StockPricePredictionDto> streamRecord) {
                final var key = streamRecord.key();
                final var ticker = streamRecord.value().ticker();
                final var value = streamRecord.value().pricePrediction();

                var topPredictionSet = predictionsStore.get(key);
                if (topPredictionSet == null) {
                    topPredictionSet = new TreeSet<>(Comparator.reverseOrder());
                } else {
                    topPredictionSet = topPredictionSet.reversed();
                }

                final var latestTopPredictionResponse = new TopPredictionResponse(ticker, value);
                final var sameTickerOptional = topPredictionSet.stream()
                    .filter(prediction -> prediction.ticker().equals(latestTopPredictionResponse.ticker()))
                    .findFirst();
                if (sameTickerOptional.isPresent()) {
                    topPredictionSet.remove(sameTickerOptional.get());
                    topPredictionSet.add(latestTopPredictionResponse);
                } else {
                    topPredictionSet.add(latestTopPredictionResponse);
                }

                while (topPredictionSet.size() > limit) {
                    topPredictionSet.removeLast();
                }

                predictionsStore.put(key, topPredictionSet);
            }
        };
    }
}
