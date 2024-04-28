package org.example.finprocessor.test;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

public class ServerWebExchangeFactory {
    public static MockServerWebExchange createGetServerRequest(String url) {
        final var remoteSocketAddress = new InetSocketAddress("192.168.1.2", 32001);
        final var node1SocketAddress = new InetSocketAddress(
            StreamsMetadataFactory.NODE1_IP, StreamsMetadataFactory.SERVER_PORT
        );
        final var serverHttpRequest = MockServerHttpRequest.get(url)
            .localAddress(node1SocketAddress)
            .remoteAddress(remoteSocketAddress)
            .build();
        return new MockServerWebExchange.Builder(serverHttpRequest)
            .build();
    }

    public static MockServerWebExchange createGetPredictionsRequest() {
        return createGetServerRequest("/api/v1/predictions");
    }

    public static MockServerWebExchange createGetVOOPredictionRequest() {
        return createGetServerRequest("/api/v1/predictions/VOO");
    }

    public static MockServerWebExchange createGetTopPredictionsRequest() {
        return createGetServerRequest("/api/v1/top/predictions");
    }

    public static MockServerWebExchange createGetLossPredictionsRequest() {
        return createGetServerRequest("/api/v1/loss/predictions");
    }

    public static MockServerWebExchange createGetPricesRequest() {
        return createGetServerRequest("/api/v1/prices");
    }
}
