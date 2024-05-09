package org.example.finprocessor.component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;

public class TracingProducerInterceptor implements ProducerInterceptor<String, Object> {
    private ObservationRegistry observationRegistry;
    private Observation observation;

    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> producerRecord) {
        observation = Observation.start("kafka-producer", observationRegistry)
            .event(() -> "onSend");
        return producerRecord;
    }

    @Override
    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {
        if (e != null) {
            observation.error(e);
        } else {
            observation.event(() ->
                String.format("onAcknowledgement offset=%d", recordMetadata.offset())
            );
        }
        observation.stop();
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
