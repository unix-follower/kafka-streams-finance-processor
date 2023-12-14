package org.example.finprocessor.exception;

import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinProcessorStreamsUncaughtExceptionHandler implements StreamsUncaughtExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(FinProcessorStreamsUncaughtExceptionHandler.class);

    @Override
    public StreamThreadExceptionResponse handle(Throwable exception) {
        logger.error(exception.getMessage(), exception);
        return StreamThreadExceptionResponse.REPLACE_THREAD;
    }
}
