package org.example.finprocessor.test;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

public class ServerWebExchangeFactory {
    public static MockServerWebExchange createGetServerRequest(String url) {
        final var serverHttpRequest = MockServerHttpRequest.get(url)
            .localAddress(InetSocketAddress.createUnresolved("localhost", 8080))
            .build();
        return new MockServerWebExchange.Builder(serverHttpRequest)
            .build();
    }
}
