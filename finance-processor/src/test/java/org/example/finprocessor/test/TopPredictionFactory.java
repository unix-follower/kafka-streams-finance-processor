package org.example.finprocessor.test;

import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;

public class TopPredictionFactory {
    public static TopPredictionResponse of(StockPricePredictionDto stockPricePrediction) {
        return new TopPredictionResponse(
            stockPricePrediction.ticker(),
            stockPricePrediction.pricePrediction()
        );
    }
}
