package org.example.finprocessor.test;

import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.StreamsConfig;
import org.example.finprocessor.config.AppProperties;
import org.example.finprocessor.config.KafkaStreamsConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

import java.util.List;
import java.util.Properties;

public class KafkaStreamsPropertiesFactory {
    private AppProperties appProperties;

    public KafkaStreamsPropertiesFactory setAppProperties(AppProperties appProperties) {
        this.appProperties = appProperties;
        return this;
    }

    public KafkaProperties createKafkaProperties() {
        final var kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of("192.168.105.6:9092"));
        final var clientId = "fin-processor";
        kafkaProperties.setClientId(clientId);

        final var consumer = kafkaProperties.getConsumer();
        consumer.setMaxPollRecords(200);
        consumer.getProperties()
            .put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "PT5m");

        final var streams = kafkaProperties.getStreams();
        streams.setApplicationId(clientId);
        streams.getProperties().put(StreamsConfig.APPLICATION_SERVER_CONFIG, "localhost:8081");
        return kafkaProperties;
    }

    public Properties create() {
        final var kafkaProperties = createKafkaProperties();
        final var config = new KafkaStreamsConfig(kafkaProperties, appProperties);
        final var observationRegistry = ObservationRegistry.create();
        return config.kafkaStreamsConfig(observationRegistry).asProperties();
    }
}
