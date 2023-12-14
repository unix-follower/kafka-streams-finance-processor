package org.example.finprocessor.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finprocessor.api.ErrorCode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public Mono<Void> handle(ServerWebExchange exchange, EntityNotFoundException e) {
        final var httpResponse = exchange.getResponse();
        setMetadata(httpResponse);

        final var writerMono = Mono.fromSupplier(() -> writeResponseBodToDataBuffer(e, httpResponse));
        return httpResponse.writeWith(writerMono);
    }

    private static void setMetadata(ServerHttpResponse httpResponse) {
        httpResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        httpResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    }

    private static Map<String, Integer> createResponseBody(AppException e) {
        return Map.of("errorCode", e.getErrorCode().getCode());
    }

    private DataBuffer writeResponseBodToDataBuffer(AppException e, ServerHttpResponse httpResponse) {
        try {
            final byte[] responseBody = objectMapper.writeValueAsBytes(createResponseBody(e));
            return httpResponse.bufferFactory().wrap(responseBody);
        } catch (JsonProcessingException ex) {
            throw new AppException(ex, ErrorCode.UNKNOWN);
        }
    }
}
