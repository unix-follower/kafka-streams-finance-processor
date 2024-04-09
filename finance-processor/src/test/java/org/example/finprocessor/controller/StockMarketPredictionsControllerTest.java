package org.example.finprocessor.controller;

import org.apache.commons.lang3.tuple.Pair;
import org.example.finprocessor.api.GetPredictionsParams;
import org.example.finprocessor.api.LossPredictionResponse;
import org.example.finprocessor.api.PredictionSearchMode;
import org.example.finprocessor.api.StockPricePredictionResponse;
import org.example.finprocessor.component.StockMarketControllerFacade;
import org.example.finprocessor.component.StockPricePredictionDtoToResponseConverter;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.test.ServerWebExchangeFactory;
import org.example.finprocessor.test.StockPriceFactory;
import org.example.finprocessor.test.StockPricePredictionDtoFactory;
import org.example.finprocessor.test.StockPricePredictionDtoUtil;
import org.example.finprocessor.test.TopPredictionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class StockMarketPredictionsControllerTest {
    private final Converter<StockPricePredictionDto, StockPricePredictionResponse> toResponseConverter
        = new StockPricePredictionDtoToResponseConverter();

    private final ArgumentCaptor<GetPredictionsParams> argumentCaptor =
        ArgumentCaptor.forClass(GetPredictionsParams.class);

    @Mock
    private StockMarketControllerFacade facadeMock;
    private StockMarketPredictionsController controller;

    @BeforeEach
    void setUp() {
        controller = new StockMarketPredictionsController(facadeMock);
    }

    private Pair<StockPricePredictionResponse, StockPricePredictionDto> createStockPricePredictionResponse() {
        final var vooETF = StockPriceFactory.vooAt20231212();
        final var pricePredictionDto = StockPricePredictionDtoFactory.of(
            vooETF, StockPricePredictionDtoUtil.closePlus1(vooETF)
        );
        return Pair.of(
            toResponseConverter.convert(pricePredictionDto),
            pricePredictionDto
        );
    }

    private void execute_test_getPredictions_with_all_search_mode(String mode) {
        // given
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        final var mockServerWebExchange = ServerWebExchangeFactory.createGetPredictionsRequest();

        Mockito.when(facadeMock.getPredictions(
                Mockito.eq(mockServerWebExchange), Mockito.any()
            ))
            .thenReturn(Flux.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getPredictions(
                mockServerWebExchange,
                mode, null, null, null
            ))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getPredictions(
            Mockito.eq(mockServerWebExchange), argumentCaptor.capture()
        );

        assertEquals(1, argumentCaptor.getAllValues().size());
        final var predictionsParams = argumentCaptor.getValue();
        assertEquals(PredictionSearchMode.ALL, predictionsParams.mode());
        assertNull(predictionsParams.from());
        assertNull(predictionsParams.to());
        assertNull(predictionsParams.prefix());
    }

    @Test
    void test_getPredictions_with_all_search_mode() {
        execute_test_getPredictions_with_all_search_mode(PredictionSearchMode.ALL.getMode());
    }

    @Test
    void test_getPredictions_with_invalid_search_mode() {
        execute_test_getPredictions_with_all_search_mode("fake");
    }

    @Test
    void test_getPredictions_with_prefixScan_search_mode() {
        // given
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        final var mockServerWebExchange = ServerWebExchangeFactory.createGetPredictionsRequest();

        Mockito.when(facadeMock.getPredictions(
                Mockito.eq(mockServerWebExchange), Mockito.any()
            ))
            .thenReturn(Flux.just(expectedResponse));

        final var prefix = "V";

        StepVerifier
            // when
            .create(controller.getPredictions(
                mockServerWebExchange,
                PredictionSearchMode.PREFIX_SCAN.getMode(),
                null,
                null,
                prefix
            ))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getPredictions(
            Mockito.eq(mockServerWebExchange), argumentCaptor.capture()
        );

        assertEquals(1, argumentCaptor.getAllValues().size());
        final var predictionsParams = argumentCaptor.getValue();
        assertEquals(PredictionSearchMode.PREFIX_SCAN, predictionsParams.mode());
        assertNull(predictionsParams.from());
        assertNull(predictionsParams.to());
        assertEquals(prefix, predictionsParams.prefix());
    }

    @Test
    void test_getPredictions_with_prefixScan_search_mode_and_prefix_is_null() {
        // given
        final var mockServerWebExchange = ServerWebExchangeFactory.createGetPredictionsRequest();

        StepVerifier
            // when
            .create(controller.getPredictions(
                mockServerWebExchange,
                PredictionSearchMode.PREFIX_SCAN.getMode(), null, null, null
            ))
            // then
            .expectErrorSatisfies(throwable -> {
                assertInstanceOf(ResponseStatusException.class, throwable);
                final var e = (ResponseStatusException) throwable;
                assertEquals(HttpStatus.BAD_REQUEST.value(), e.getStatusCode().value());
            })
            .verify();

        Mockito.verify(facadeMock, Mockito.never()).getPredictions(Mockito.any(), Mockito.any());
    }

    @Test
    void test_getPredictionsFromLocalStore_with_all_search_mode() {
        // given
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        Mockito.when(facadeMock.getPredictionsFromLocalStore(Mockito.any()))
            .thenReturn(Flux.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getPredictionsFromLocalStore(
                PredictionSearchMode.ALL.getMode(), null, null, null
            ))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getPredictionsFromLocalStore(
            argumentCaptor.capture()
        );

        assertEquals(1, argumentCaptor.getAllValues().size());
        final var predictionsParams = argumentCaptor.getValue();
        assertEquals(PredictionSearchMode.ALL, predictionsParams.mode());
        assertNull(predictionsParams.from());
        assertNull(predictionsParams.to());
        assertNull(predictionsParams.prefix());
    }

    @Test
    void test_getPredictionsFromLocalStore_with_prefixScan_search_mode_and_prefix_is_null() {
        StepVerifier
            // when
            .create(controller.getPredictionsFromLocalStore(
                PredictionSearchMode.PREFIX_SCAN.getMode(), null, null, null
            ))
            // then
            .expectErrorSatisfies(throwable -> {
                assertInstanceOf(ResponseStatusException.class, throwable);
                final var e = (ResponseStatusException) throwable;
                assertEquals(HttpStatus.BAD_REQUEST.value(), e.getStatusCode().value());
            })
            .verify();

        Mockito.verify(facadeMock, Mockito.never()).getPredictionsFromLocalStore(Mockito.any());
    }

    @Test
    void test_getPredictionByTicker() {
        // given
        final var mockServerWebExchange = ServerWebExchangeFactory.createGetVOOPredictionRequest();
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        final var ticker = "VOO";
        Mockito.when(facadeMock.getPredictionByTicker(mockServerWebExchange, ticker))
            .thenReturn(Mono.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getPredictionByTicker(mockServerWebExchange, ticker))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only())
            .getPredictionByTicker(mockServerWebExchange, ticker);
    }

    @Test
    void test_getTopPredictionsFromLocalStore() {
        // given
        final var stockPricePredictionDto = createStockPricePredictionResponse()
            .getRight();
        final var expectedResponse = TopPredictionFactory.of(stockPricePredictionDto);

        Mockito.when(facadeMock.getTopPredictionsFromLocalStore())
            .thenReturn(Flux.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getTopPredictionsFromLocalStore())
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getTopPredictionsFromLocalStore();
    }

    @Test
    void test_getTopPredictions() {
        // given
        final var mockServerWebExchange = ServerWebExchangeFactory.createGetTopPredictionsRequest();
        final var stockPricePredictionDto = createStockPricePredictionResponse()
            .getRight();
        final var expectedResponse = TopPredictionFactory.of(stockPricePredictionDto);

        Mockito.when(facadeMock.getTopPredictions(mockServerWebExchange))
            .thenReturn(Flux.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getTopPredictions(mockServerWebExchange))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getTopPredictions(mockServerWebExchange);
    }

    @Test
    void test_getLossPredictions() {
        // given
        final var mockServerWebExchange = ServerWebExchangeFactory.createGetLossPredictionsRequest();
        final var expectedResponse = new LossPredictionResponse("VOO", BigDecimal.valueOf(341.3360107421874));

        Mockito.when(facadeMock.getLossPredictions(mockServerWebExchange))
            .thenReturn(Flux.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getLossPredictions(mockServerWebExchange))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getLossPredictions(mockServerWebExchange);
    }

    @Test
    void test_getLossPredictionsFromLocalStore() {
        // given
        final var expectedResponse = new LossPredictionResponse("VOO", BigDecimal.valueOf(341.3360107421874));

        Mockito.when(facadeMock.getLossPredictionsFromLocalStore())
            .thenReturn(Flux.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getLossPredictionsFromLocalStore())
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getLossPredictionsFromLocalStore();
    }
}
