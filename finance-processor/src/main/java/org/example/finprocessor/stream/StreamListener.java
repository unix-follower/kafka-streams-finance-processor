package org.example.finprocessor.stream;

import org.apache.kafka.streams.StreamsBuilder;

public interface StreamListener {
    void listen(StreamsBuilder streamsBuilder);
}
