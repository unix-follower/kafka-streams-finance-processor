package org.example.finprocessor.api;

import org.example.finprocessor.stockmarket.api.StockPrice;

import java.time.OffsetDateTime;
import java.util.List;

public record StockPriceWindowResponse(
    OffsetDateTime eventTime,
    OffsetDateTime windowStart,
    OffsetDateTime windowEnd,
    List<StockPrice> prices
) {
}
