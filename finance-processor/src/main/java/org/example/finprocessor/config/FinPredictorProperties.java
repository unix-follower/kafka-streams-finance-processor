package org.example.finprocessor.config;

import java.net.URL;
import java.time.Duration;

public record FinPredictorProperties(
    URL url,
    Duration connectionTimeout,
    Duration readTimeout,
    Duration connectionMaxIdleTime,
    Duration connectionMaxLifeTime,
    Duration connectionEvictionTime
) {
}
