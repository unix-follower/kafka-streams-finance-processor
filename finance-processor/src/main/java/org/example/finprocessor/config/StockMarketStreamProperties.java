package org.example.finprocessor.config;

import org.example.finprocessor.stream.WindowSuppressType;
import org.example.finprocessor.stream.WindowType;

import java.time.Duration;
import java.util.Set;

public record StockMarketStreamProperties(
    Set<String> inputTopics,
    String outputTopic,
    Duration window,
    Duration windowGracePeriod,
    WindowType windowType,
    WindowSuppressType windowSuppressType,
    Duration windowSuppressAwaitMoreEvents,
    int windowSuppressMaxRecords
) {
}
