package org.example.finprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("app")
public record AppProperties(
    @NestedConfigurationProperty CorsProperties cors,
    @NestedConfigurationProperty FinProcessorProperties finProcessor,
    @NestedConfigurationProperty FinPredictorProperties finPredictor,
    @NestedConfigurationProperty StockMarketRouterStreamProperties stockMarketRouterStream,
    @NestedConfigurationProperty StockMarketStreamProperties stockMarketStream,
    @NestedConfigurationProperty TopPredictionsStreamProperties topPredictionsStream,
    @NestedConfigurationProperty LossPredictionsStreamProperties lossPredictionsStream
) {
}
