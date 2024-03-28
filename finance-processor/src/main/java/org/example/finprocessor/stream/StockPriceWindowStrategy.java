package org.example.finprocessor.stream;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Merger;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.streams.state.WindowStore;
import org.example.finprocessor.config.StockMarketStreamProperties;
import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.util.Constants;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface StockPriceWindowStrategy {
    KTable<Windowed<String>, List<StockPrice>> apply(KGroupedStream<String, StockPrice> stockPriceKGroupedStream);
}

class WindowStrategy implements StockPriceWindowStrategy {
    private final StockMarketStreamProperties streamProperties;
    private final Serde<List<StockPrice>> valueSerde;

    public WindowStrategy(StockMarketStreamProperties streamProperties, Serde<List<StockPrice>> valueSerde) {
        this.streamProperties = streamProperties;
        this.valueSerde = valueSerde;
    }

    public KTable<Windowed<String>, List<StockPrice>> apply(KGroupedStream<String, StockPrice> groupedStream) {
        final var windowType = streamProperties.windowType();

        final KTable<Windowed<String>, List<StockPrice>> windowedAggregateKTable;
        switch (windowType) {
            case TIME -> windowedAggregateKTable = applyTimeWindow(
                streamProperties, groupedStream, valueSerde
            );
            case SESSION -> windowedAggregateKTable = applySessionWindow(
                streamProperties, groupedStream, valueSerde
            );
            case SLIDING -> windowedAggregateKTable = applySlidingWindow(
                streamProperties, groupedStream, valueSerde
            );

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
        StockMarketStreamProperties streamProperties,
        KGroupedStream<String, StockPrice> groupedStream,
        Serde<List<StockPrice>> valueSerde
    ) {
        final var window = streamProperties.window();
        final var gracePeriod = streamProperties.windowGracePeriod();

        final TimeWindows timeWindows;
        if (gracePeriod != null && gracePeriod.isPositive()) {
            timeWindows = hoppingWindow(window, gracePeriod);
        } else {
            timeWindows = tumblingWindow(window);
        }

        final var windowStoreMaterialized =
            materializeToWindowStore(valueSerde);
        return groupedStream
            .windowedBy(timeWindows)
            .aggregate(
                ArrayList::new,
                this::stockPriceListAggregator,
                windowStoreMaterialized
            );
    }

    private static Materialized<String, List<StockPrice>, WindowStore<Bytes, byte[]>> materializeToWindowStore(
        Serde<List<StockPrice>> valueSerde
    ) {
        return Materialized
            .<String, List<StockPrice>, WindowStore<Bytes, byte[]>>as(Constants.PREDICTIONS_TIME_WINDOWED_STORE)
            .withValueSerde(valueSerde);
    }

    private KTable<Windowed<String>, List<StockPrice>> applySessionWindow(
        StockMarketStreamProperties streamProperties,
        KGroupedStream<String, StockPrice> groupedStream,
        Serde<List<StockPrice>> valueSerde
    ) {
        final var window = streamProperties.window();
        final var gracePeriod = streamProperties.windowGracePeriod();

        final SessionWindows sessionWindows;
        if (gracePeriod != null && gracePeriod.isPositive()) {
            sessionWindows = SessionWindows.ofInactivityGapAndGrace(window, gracePeriod);
        } else {
            sessionWindows = SessionWindows.ofInactivityGapWithNoGrace(window);
        }

        final var sessionStoreMaterialized =
            Materialized.<
                    String, List<StockPrice>, SessionStore<Bytes, byte[]>
                    >as(Constants.PREDICTIONS_SESSION_WINDOWED_STORE)
                .withValueSerde(valueSerde);

        final Merger<String, List<StockPrice>> stockPriceSessionMerger = (aggKey, aggOne, aggTwo) -> {
            aggOne.addAll(aggTwo);
            return aggOne;
        };

        return groupedStream
            .windowedBy(sessionWindows)
            .aggregate(
                ArrayList::new,
                this::stockPriceListAggregator,
                stockPriceSessionMerger,
                sessionStoreMaterialized
            );
    }

    private KTable<Windowed<String>, List<StockPrice>> applySlidingWindow(
        StockMarketStreamProperties streamProperties,
        KGroupedStream<String, StockPrice> groupedStream,
        Serde<List<StockPrice>> valueSerde
    ) {
        final var window = streamProperties.window();
        final var gracePeriod = streamProperties.windowGracePeriod();

        final SlidingWindows slidingWindows;
        if (gracePeriod != null && gracePeriod.isPositive()) {
            slidingWindows = SlidingWindows.ofTimeDifferenceAndGrace(window, gracePeriod);
        } else {
            slidingWindows = SlidingWindows.ofTimeDifferenceWithNoGrace(window);
        }

        final var windowStoreMaterialized =
            materializeToWindowStore(valueSerde);
        return groupedStream
            .windowedBy(slidingWindows)
            .aggregate(
                ArrayList::new,
                this::stockPriceListAggregator,
                windowStoreMaterialized
            );
    }

    private List<StockPrice> stockPriceListAggregator(String key, StockPrice stockPrice, List<StockPrice> aggregate) {
        aggregate.add(stockPrice);
        return aggregate;
    }
}
