package org.example.finprocessor.api;

import java.time.OffsetDateTime;

public record GetPricesParams(
    PriceSearchMode mode,
    String key,
    String keyFrom,
    String keyTo,
    OffsetDateTime timeFrom,
    OffsetDateTime timeTo
) {
}
