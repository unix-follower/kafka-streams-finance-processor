package org.example.finprocessor.component;

import org.example.finprocessor.api.StockPricePredictionResponse;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.springframework.core.convert.converter.Converter;

public class StockPricePredictionDtoToResponseConverter implements Converter<StockPricePredictionDto, StockPricePredictionResponse> {
    @Override
    public StockPricePredictionResponse convert(StockPricePredictionDto source) {
        return new StockPricePredictionResponse(
            source.ticker(),
            source.openRangeAt(),
            source.closedRangeAt(),
            source.predictionAt(),
            source.pricePrediction()
        );
    }
}
