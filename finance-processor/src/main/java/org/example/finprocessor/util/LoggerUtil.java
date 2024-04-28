package org.example.finprocessor.util;

import org.slf4j.Logger;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.List;
import java.util.Objects;

public class LoggerUtil {
    private LoggerUtil() {
    }

    public static void printStreamsAppHosts(Logger logger, ServerHttpRequest request, List<String> urls) {
        if (logger.isDebugEnabled()) {
            final var localAddress = Objects.requireNonNull(request.getLocalAddress());
            final int hostPort = localAddress.getPort();
            final var hostIp = localAddress.getAddress().getHostAddress();

            logger.debug("LocalAddress={}:{}. Remote hosts: {}", hostIp, hostPort, urls);
        }
    }
}
