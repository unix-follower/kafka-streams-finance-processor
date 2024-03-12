package org.example.finprocessor.stockmarket.api;

public class StockTypeCsvRecord {
    private StockType type;
    private String ticker;

    public StockType getType() {
        return type;
    }

    public StockTypeCsvRecord setType(StockType type) {
        this.type = type;
        return this;
    }

    public String getTicker() {
        return ticker;
    }

    public StockTypeCsvRecord setTicker(String ticker) {
        this.ticker = ticker;
        return this;
    }
}
