package org.example.finprocessor.stockmarket.api;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockPrice(
    String ticker,
    @JsonFormat(timezone = "America/New_York")
    LocalDateTime date,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal adjClose,
    BigDecimal volume,
    BigDecimal dividends,
    BigDecimal stockSplits,
    BigDecimal capitalGains
) {
}
