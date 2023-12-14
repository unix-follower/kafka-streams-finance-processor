package org.example.finprocessor.config;

import java.time.Duration;

public record FinProcessorProperties(
    Duration connectionTimeout,
    Duration readTimeout,
    Duration connectionMaxIdleTime,
    Duration connectionMaxLifeTime,
    Duration connectionEvictionTime
) {
}
