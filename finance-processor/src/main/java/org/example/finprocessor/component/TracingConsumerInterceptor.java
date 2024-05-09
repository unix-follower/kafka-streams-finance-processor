package org.example.finprocessor.component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.Map;

public class TracingConsumerInterceptor implements ConsumerInterceptor<String, Object> {
    private ObservationRegistry observationRegistry;
    private Observation observation;

    @Override
    public ConsumerRecords<String, Object> onConsume(ConsumerRecords<String, Object> records) {
        observation = Observation.start("kafka-consumer", observationRegistry)
            .event(() -> String.format("onConsume(record-count=%d)", records.count()));
        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        observation.event(() -> String.format("onCommit(offset-count=%d)", offsets.size())).stop();
    }

    @Override
    public void close() {
        if (observation != null) {
            observation.stop();
        }
    }

    @Override
    public void configure(Map<String, ?> map) {
        observationRegistry = (ObservationRegistry) map.get(ObservationRegistry.class.getName());
    }
}
