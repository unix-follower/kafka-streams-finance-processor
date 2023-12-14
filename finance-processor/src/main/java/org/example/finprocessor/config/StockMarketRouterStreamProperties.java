package org.example.finprocessor.config;

public record StockMarketRouterStreamProperties(
    String inputTopic,
    String shareTopicSuffix,
    String etfTopicSuffix,
    String fundTopicSuffix
) {
    public String getETFTopicName() {
        return inputTopic + etfTopicSuffix;
    }

    public String getFundTopicName() {
        return inputTopic + fundTopicSuffix;
    }

    public String getShareTopicName() {
        return inputTopic + shareTopicSuffix;
    }
}
