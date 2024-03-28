package org.example.finprocessor.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

@RequestMapping("/api/v1")
public interface StockMarketPricesApi {
    @GetMapping("/prices")
    Flux<StockPriceWindowResponse> getPrices(
        ServerWebExchange exchange,
        @RequestParam String mode,
        @RequestParam(required = false) String key,
        @RequestParam(required = false) String keyFrom,
        @RequestParam(required = false) String keyTo,
        @RequestParam(required = false) OffsetDateTime timeFrom,
        @RequestParam(required = false) OffsetDateTime timeTo
    );

    @GetMapping("/local/prices")
    Flux<StockPriceWindowResponse> getPricesFromLocalStore(
        @RequestParam String mode,
        @RequestParam(required = false) String key,
        @RequestParam(required = false) String keyFrom,
        @RequestParam(required = false) String keyTo,
        @RequestParam(required = false) OffsetDateTime timeFrom,
        @RequestParam(required = false) OffsetDateTime timeTo
    );
}
