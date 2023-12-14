package org.example.finprocessor.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.NavigableSet;

public class NavigableSetJsonSerde<T extends Comparable<T>> implements Serde<NavigableSet<T>> {
    private final Serializer<NavigableSet<T>> serializer;
    private final Deserializer<NavigableSet<T>> deserializer;

    public NavigableSetJsonSerde(ObjectMapper objectMapper, Class<T> clazz) {
        final var javaType = TypeFactory.defaultInstance().constructParametricType(NavigableSet.class, clazz);
        final var serde = Serdes.serdeFrom(
            new JsonSerializer<>(javaType, objectMapper),
            new JsonDeserializer<NavigableSet<T>>(javaType, objectMapper)
        );
        this.serializer = serde.serializer();
        this.deserializer = serde.deserializer();
    }

    @Override
    public Serializer<NavigableSet<T>> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<NavigableSet<T>> deserializer() {
        return deserializer;
    }

    @Override
    public void close() {
        serializer.close();
        deserializer.close();
    }
}
