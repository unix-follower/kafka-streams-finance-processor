package org.example.finprocessor.test;

import org.example.finprocessor.stockmarket.api.StockPrice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StockPriceFactory {
    public static StockPrice vooAt20231212() {
        final var open = BigDecimal.valueOf(424.2200012207031);
        final var high = BigDecimal.valueOf(426.6700134277344);
        final var low = BigDecimal.valueOf(423.2699890136719);
        final var close = BigDecimal.valueOf(426.6700134277344);
        final var adjustedClose = BigDecimal.valueOf(426.6700134277344);
        final var volume = BigDecimal.valueOf(5978200.0);
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final var capitalGains = BigDecimal.ZERO;
        return new StockPrice(
            "VOO",
            LocalDateTime.parse("2023-12-12T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }

    public static StockPrice spyAt20231212() {
        final var open = BigDecimal.valueOf(461.6300048828125);
        final var high = BigDecimal.valueOf(464.20001220703125);
        final var low = BigDecimal.valueOf(460.6000061035156);
        final var close = BigDecimal.valueOf(464.1000061035156);
        final var adjustedClose = BigDecimal.valueOf(464.1000061035156);
        final var volume = new BigDecimal("67908700.0");
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final var capitalGains = BigDecimal.ZERO;
        return new StockPrice(
            "SPY",
            LocalDateTime.parse("2023-12-12T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }

    public static StockPrice googlAt20231212() {
        final var open = BigDecimal.valueOf(131.80999755859375);
        final var high = BigDecimal.valueOf(133.0);
        final var low = BigDecimal.valueOf(131.25999450683594);
        final var close = BigDecimal.valueOf(132.52000427246094);
        final var adjustedClose = BigDecimal.valueOf(132.52000427246094);
        final var volume = new BigDecimal("28982900.0");
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final BigDecimal capitalGains = null;
        return new StockPrice(
            "GOOGL",
            LocalDateTime.parse("2023-12-12T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }

    public static StockPrice vtsaxAt20231212() {
        final var open = BigDecimal.valueOf(112.29000091552734);
        final var high = BigDecimal.valueOf(112.29000091552734);
        final var low = BigDecimal.valueOf(112.29000091552734);
        final var close = BigDecimal.valueOf(112.29000091552734);
        final var adjustedClose = BigDecimal.valueOf(112.29000091552734);
        final var volume = BigDecimal.ZERO;
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final var capitalGains = BigDecimal.ZERO;
        return new StockPrice(
            "VTSAX",
            LocalDateTime.parse("2023-12-12T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }

    public static StockPrice nvdaAt20240221() {
        final var open = BigDecimal.valueOf(719.469970703125);
        final var high = BigDecimal.valueOf(688.8800048828125);
        final var low = BigDecimal.valueOf(666.1300048828125);
        final var close = BigDecimal.valueOf(669.8599853515625);
        final var adjustedClose = BigDecimal.valueOf(669.8599853515625);
        final var volume = BigDecimal.valueOf(38033989);
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final var capitalGains = BigDecimal.ZERO;
        return new StockPrice(
            "NVDA",
            LocalDateTime.parse("2024-02-21T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }

    public static StockPrice aaplAt20240221() {
        final var open = BigDecimal.valueOf(181.7899932861328);
        final var high = BigDecimal.valueOf(182.8887939453125);
        final var low = BigDecimal.valueOf(180.66000366210938);
        final var close = BigDecimal.valueOf(180.8699951171875);
        final var adjustedClose = BigDecimal.valueOf(180.8699951171875);
        final var volume = BigDecimal.valueOf(24404403);
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final var capitalGains = BigDecimal.ZERO;
        return new StockPrice(
            "AAPL",
            LocalDateTime.parse("2024-02-21T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }

    public static StockPrice amznAt20240221() {
        final var open = BigDecimal.valueOf(167.8300018310547);
        final var high = BigDecimal.valueOf(170.22999572753906);
        final var low = BigDecimal.valueOf(167.41000366210938);
        final var close = BigDecimal.valueOf(167.77999877929688);
        final var adjustedClose = BigDecimal.valueOf(167.77999877929688);
        final var volume = BigDecimal.valueOf(30429048);
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final var capitalGains = BigDecimal.ZERO;
        return new StockPrice(
            "AMZN",
            LocalDateTime.parse("2024-02-21T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }

    public static StockPrice maAt20240221() {
        final var open = BigDecimal.valueOf(452.79998779296875);
        final var high = BigDecimal.valueOf(457.6886901855469);
        final var low = BigDecimal.valueOf(452.0);
        final var close = BigDecimal.valueOf(456.3699951171875);
        final var adjustedClose = BigDecimal.valueOf(456.3699951171875);
        final var volume = BigDecimal.valueOf(1509609);
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final var capitalGains = BigDecimal.ZERO;
        return new StockPrice(
            "MA",
            LocalDateTime.parse("2024-02-21T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }

    public static StockPrice msftAt20240221() {
        final var open = BigDecimal.valueOf(403.239990234375);
        final var high = BigDecimal.valueOf(400.7099914550781);
        final var low = BigDecimal.valueOf(397.2200012207031);
        final var close = BigDecimal.valueOf(398.3699951171875);
        final var adjustedClose = BigDecimal.valueOf(398.3699951171875);
        final var volume = BigDecimal.valueOf(10115189);
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final var capitalGains = BigDecimal.ZERO;
        return new StockPrice(
            "MSFT",
            LocalDateTime.parse("2024-02-21T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }

    public static StockPrice tslaAt20240221() {
        final var open = BigDecimal.valueOf(196.1300048828125);
        final var high = BigDecimal.valueOf(199.44000244140625);
        final var low = BigDecimal.valueOf(191.9499969482422);
        final var close = BigDecimal.valueOf(193.40130615234375);
        final var adjustedClose = BigDecimal.valueOf(193.40130615234375);
        final var volume = BigDecimal.valueOf(84875625);
        final var dividends = BigDecimal.ZERO;
        final var stockSplits = BigDecimal.ZERO;
        final var capitalGains = BigDecimal.ZERO;
        return new StockPrice(
            "TSLA",
            LocalDateTime.parse("2024-02-21T00:00:00"),
            open,
            high,
            low,
            close,
            adjustedClose,
            volume,
            dividends,
            stockSplits,
            capitalGains
        );
    }
}
