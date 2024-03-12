package org.example.finprocessor.util;

import org.slf4j.Logger;

import java.util.function.Supplier;

public class LoggerUtil {
    private LoggerUtil() {
    }

    public static void debug(Logger logger, Supplier<String> messageSupplier, Object o) {
        if (logger.isDebugEnabled()) {
            logger.debug(messageSupplier.get(), o);
        }
    }
}
