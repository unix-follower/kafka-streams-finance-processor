package org.example.finprocessor.test;

import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.util.DateUtil;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class StockPricePredictionDtoFactory {
    public static StockPricePredictionDto of(StockPrice stockPrice, BigDecimal pricePrediction) {
        return of(List.of(stockPrice), pricePrediction);
    }

    public static StockPricePredictionDto of(List<StockPrice> stockPriceList, BigDecimal pricePrediction) {
        final var firstStockPrice = stockPriceList.getFirst();
        final var lastStockPrice = stockPriceList.getLast();

        final var openDate = DateUtil.estToUtc(firstStockPrice.date());
        final var closeDate = DateUtil.estToUtc(lastStockPrice.date());

        final var predictionAt = OffsetDateTime.now(ZoneOffset.UTC);

        return new StockPricePredictionDto(
            firstStockPrice.ticker(),
            openDate,
            lastStockPrice.close(),
            closeDate,
            predictionAt,
            pricePrediction
        );
    }
}
