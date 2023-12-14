package org.example.finprocessor.test;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;

import java.util.Collection;
import java.util.Iterator;

public class InMemoryKeyValueIterator<T> implements KeyValueIterator<String, T> {
    private final Iterator<KeyValue<String, T>> iterator;

    public InMemoryKeyValueIterator(Collection<KeyValue<String, T>> data) {
        this.iterator = data.iterator();
    }

    @Override
    public void close() {
    }

    @Override
    public String peekNextKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public KeyValue<String, T> next() {
        return iterator.next();
    }
}
