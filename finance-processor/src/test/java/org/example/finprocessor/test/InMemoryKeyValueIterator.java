package org.example.finprocessor.test;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;

import java.util.Collection;
import java.util.Iterator;

public class InMemoryKeyValueIterator<K, V> implements KeyValueIterator<K, V> {
    private final Iterator<KeyValue<K, V>> iterator;

    public InMemoryKeyValueIterator(Collection<KeyValue<K, V>> data) {
        this.iterator = data.iterator();
    }

    @Override
    public void close() {
    }

    @Override
    public K peekNextKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public KeyValue<K, V> next() {
        return iterator.next();
    }
}
