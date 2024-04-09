package org.example.finprocessor.util;

import org.apache.kafka.streams.state.HostInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Objects;

public class ServerUtil {
    private static final Logger logger = LoggerFactory.getLogger(ServerUtil.class);

    private ServerUtil() {
    }

    public static boolean isSameHost(HostInfo hostInfo, ServerHttpRequest request) {
        final var localAddress = Objects.requireNonNull(request.getLocalAddress());
        final int hostPort = localAddress.getPort();
        final var hostIp = localAddress.getAddress().getHostAddress();

        if (logger.isDebugEnabled()) {
            logger.debug(
                "HostInfo={}:{} LocalAddress={}:{}",
                hostInfo.host(), hostInfo.port(), hostIp, hostPort
            );
        }
        return hostInfo.host().equals(hostIp) && hostInfo.port() == hostPort;
    }

    public static boolean isActiveHost(ServerHttpRequest request, HostInfo hostInfo) {
        return isSameHost(hostInfo, request);
    }
}
