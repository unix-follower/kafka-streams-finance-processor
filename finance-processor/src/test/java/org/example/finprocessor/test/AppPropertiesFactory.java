package org.example.finprocessor.test;

import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.config.CorsProperties;
import org.example.finprocessor.config.FinPredictorProperties;
import org.example.finprocessor.config.FinProcessorProperties;
import org.example.finprocessor.config.LossPredictionsStreamProperties;
import org.example.finprocessor.config.StockMarketRouterStreamProperties;
import org.example.finprocessor.config.StockMarketStreamProperties;
import org.example.finprocessor.config.TopPredictionsStreamProperties;
import org.example.finprocessor.stream.WindowSuppressType;
import org.example.finprocessor.stream.WindowType;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

public class AppPropertiesFactory {
    public static final Duration DURATION_10_SEC = Duration.ofSeconds(10);
    public static final Duration DURATION_30_SEC = Duration.ofSeconds(30);
    public static final Duration DURATION_90_SEC = Duration.ofSeconds(90);
    private static final int TOP_PREDICTION_LIMIT = 20;
    public static final double PERCENT_20 = 0.2;

    private final Supplier<FinProcessorProperties> finProcessorPropertiesSupplier = () ->
        new FinProcessorProperties(
            DURATION_30_SEC,
            DURATION_30_SEC,
            DURATION_30_SEC,
            DURATION_30_SEC,
            DURATION_90_SEC
        );

    private final Supplier<StockMarketRouterStreamProperties> routerPropertiesSupplier = () ->
        new StockMarketRouterStreamProperties(
            "stock-market",
            "-share",
            "-etf",
            "-fund"
        );

    private final Supplier<FinPredictorProperties> finPredictorPropertiesSupplier = () -> {
        try {
            return new FinPredictorProperties(
                URI.create("http://localhost:5000").toURL(),
                DURATION_30_SEC,
                DURATION_30_SEC,
                DURATION_30_SEC,
                DURATION_30_SEC,
                DURATION_90_SEC
            );
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    };

    private final Supplier<CorsProperties> corsPropertiesSupplier = () -> new CorsProperties(
        "http://localhost:3000",
        Duration.ofHours(1),
        Set.of("*")
    );

    private final Supplier<StockMarketStreamProperties> stockMarketStreamPropertiesSupplier = () ->
        new StockMarketStreamProperties(
            Set.of(
                "-etf",
                "-fund",
                "-share"
            ),
            "predictions",
                DURATION_10_SEC,
                null,
                WindowType.SESSION,
                WindowSuppressType.WINDOW_CLOSE,
                DURATION_30_SEC,
                100
            );

    private Supplier<TopPredictionsStreamProperties> topPredictionsStreamPropertiesSupplier = () ->
        new TopPredictionsStreamProperties(
            TOP_PREDICTION_LIMIT
        );

    private final Supplier<LossPredictionsStreamProperties> lossPredictionsStreamPropertiesSupplier = () ->
        new LossPredictionsStreamProperties(PERCENT_20);

    public void setTopPredictionsStreamPropertiesSupplier(
        Supplier<TopPredictionsStreamProperties> topPredictionsStreamPropertiesSupplier
    ) {
        this.topPredictionsStreamPropertiesSupplier = topPredictionsStreamPropertiesSupplier;
    }

    public AppProperties create() {
        final var finProcessorProperties = finProcessorPropertiesSupplier.get();
        final var routerProperties = routerPropertiesSupplier.get();
        final var finPredictorProperties = finPredictorPropertiesSupplier.get();
        final var corsProperties = corsPropertiesSupplier.get();
        return new AppProperties(
            corsProperties,
            finProcessorProperties,
            finPredictorProperties,
            routerProperties,
            stockMarketStreamPropertiesSupplier.get(),
            topPredictionsStreamPropertiesSupplier.get(),
            lossPredictionsStreamPropertiesSupplier.get()
        );
    }
}
