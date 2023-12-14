package org.example.finprocessor.exception;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FinProcessorProductionExceptionHandler implements ProductionExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(FinProcessorProductionExceptionHandler.class);

    @Override
    public ProductionExceptionHandlerResponse handle(ProducerRecord<byte[], byte[]> producerRecord, Exception exception) {
        logger.error(exception.getMessage(), exception);
        return ProductionExceptionHandlerResponse.CONTINUE;
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // There is nothing to configure
    }
}
