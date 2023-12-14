package org.example.finprocessor.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.List;

public class SerdeUtil {
    private SerdeUtil() {
    }

    public static <T> Serde<T> createJsonSerde(ObjectMapper objectMapper, Class<T> clazz) {
        return Serdes.serdeFrom(
            new JsonSerializer<>(objectMapper),
            new JsonDeserializer<>(clazz, objectMapper)
        );
    }

    public static Serde<StockPrice> createStockPriceSerde(ObjectMapper objectMapper) {
        return createJsonSerde(objectMapper, StockPrice.class);
    }

    public static Serde<StockPricePredictionDto> createPredictionSerde(ObjectMapper objectMapper) {
        return createJsonSerde(objectMapper, StockPricePredictionDto.class);
    }

    public static Serde<List<TopPredictionResponse>> createTopPredictionSerde(ObjectMapper objectMapper) {
        return Serdes.serdeFrom(
            new JsonSerializer<>(objectMapper),
            new JsonDeserializer<>(new TypeReference<>() {
            }, objectMapper)
        );
    }
}
