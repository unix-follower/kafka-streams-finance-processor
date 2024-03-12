package org.example.finprocessor.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.example.finprocessor.component.LossCalculator;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.util.Constants;
import org.example.finprocessor.util.SerdeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LossPredictionsListener implements StreamListener {
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public LossPredictionsListener(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    @Autowired
    public void listen(StreamsBuilder streamsBuilder) {
        final var stringSerde = Serdes.String();
        final var predictionSerde = SerdeUtil.createPredictionSerde(objectMapper);
        final var consumed = Consumed.with(stringSerde, predictionSerde);

        final var inputTopic = appProperties.stockMarketStream().outputTopic();
        final var lossPercent = appProperties.lossPredictionsStream().percent();
        streamsBuilder.stream(inputTopic, consumed)
            .filter((key, value) -> LossCalculator.isLossThresholdExceeded(
                value.close(),
                value.pricePrediction(),
                lossPercent
            ))
            .mapValues((key, value) -> value.pricePrediction().toPlainString())
            .toTable(
                Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(Constants.LOSS_PREDICTIONS_STORE)
                    .withKeySerde(stringSerde)
                    .withValueSerde(stringSerde)
            );
    }
}
