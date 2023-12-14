package org.example.finprocessor.util;

import org.apache.kafka.streams.state.HostInfo;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Objects;

public class ServerUtil {
    private ServerUtil() {
    }

    public static boolean isSameHost(HostInfo hostInfo, ServerHttpRequest request) {
        final var localAddress = Objects.requireNonNull(request.getLocalAddress());
        final int hostPort = localAddress.getPort();
        final var hostName = localAddress.getHostName();

        return hostInfo.host().equals(hostName) && hostInfo.port() == hostPort;
    }

    public static boolean isActiveHost(ServerHttpRequest request, HostInfo hostInfo) {
        return isSameHost(hostInfo, request);
    }
}
