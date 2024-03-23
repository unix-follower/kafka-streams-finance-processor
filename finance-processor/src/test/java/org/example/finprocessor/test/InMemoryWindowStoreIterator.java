package org.example.finprocessor.test;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.WindowStoreIterator;

import java.util.Collection;
import java.util.Iterator;

public class InMemoryWindowStoreIterator<V> implements WindowStoreIterator<V> {
    private final Iterator<KeyValue<Long, V>> iterator;

    public InMemoryWindowStoreIterator(Collection<KeyValue<Long, V>> data) {
        this.iterator = data.iterator();
    }

    @Override
    public void close() {
    }

    @Override
    public Long peekNextKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public KeyValue<Long, V> next() {
        return iterator.next();
    }
}
