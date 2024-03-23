package org.example.finprocessor.controller;

import org.apache.commons.lang3.tuple.Pair;
import org.example.finprocessor.api.GetPricesParams;
import org.example.finprocessor.api.PriceSearchMode;
import org.example.finprocessor.api.StockPriceWindowResponse;
import org.example.finprocessor.component.StockMarketPricesControllerFacade;
import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.test.ServerWebExchangeFactory;
import org.example.finprocessor.test.StockPriceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.example.finprocessor.controller.StockMarketPricesController.INVALID_SEARCH_MODE_ERR_MSG;
import static org.example.finprocessor.controller.StockMarketPricesController.PARAM_IS_NULL_ERR_MSG_FORMAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class StockMarketPricesControllerTest {
    private static final String KEY = "VOO";

    private final ArgumentCaptor<GetPricesParams> argumentCaptor = ArgumentCaptor.forClass(GetPricesParams.class);

    private StockMarketPricesController controller;

    @Mock
    private StockMarketPricesControllerFacade facadeMock;

    @BeforeEach
    void setUp() {
        controller = new StockMarketPricesController(facadeMock);
    }

    private Pair<StockPriceWindowResponse, StockPrice> createStockPricePredictionResponse() {
        final var vooETF = StockPriceFactory.vooAt20231212();

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);

        final var expectedResponse = new StockPriceWindowResponse(
            null,
            windowStart,
            windowEnd,
            List.of(vooETF)
        );

        return Pair.of(expectedResponse, vooETF);
    }

    @Test
    void test_invalid_search_mode() {
        // given
        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        StepVerifier
            // when
            .create(controller.getPrices(
                mockServerWebExchange,
                "fake", KEY, null, null, null, null
            ))
            // then
            .expectErrorSatisfies(throwable -> assertBadRequest(INVALID_SEARCH_MODE_ERR_MSG, throwable))
            .verify();

        verifyNoFacadeInvocationForGetPrices(mockServerWebExchange);
    }

    private void verifyNoFacadeInvocationForGetPrices(MockServerWebExchange mockServerWebExchange) {
        Mockito.verify(facadeMock, Mockito.never()).getPrices(
            Mockito.eq(mockServerWebExchange), Mockito.any()
        );
    }

    @Test
    void test_get_from_local_store_with_invalid_search_mode() {
        // given
        final var searchMode = "fake";

        StepVerifier
            // when
            .create(controller.getPricesFromLocalStore(
                searchMode, KEY, null, null, null, null
            ))
            // then
            .expectErrorSatisfies(throwable -> assertBadRequest(INVALID_SEARCH_MODE_ERR_MSG, throwable))
            .verify();

        Mockito.verify(facadeMock, Mockito.never()).getPricesFromLocalStore(Mockito.any());
    }

    @Test
    void test_get_from_local_store_with_session_fetch_mode() {
        // given
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        Mockito.when(facadeMock.getPricesFromLocalStore(Mockito.any()))
            .thenReturn(Flux.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getPricesFromLocalStore(
                PriceSearchMode.SESSION_FETCH.getMode(), KEY, null, null, null, null
            ))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getPricesFromLocalStore(argumentCaptor.capture());

        assertEquals(1, argumentCaptor.getAllValues().size());
        final var capturedParams = argumentCaptor.getValue();
        assertEquals(PriceSearchMode.SESSION_FETCH, capturedParams.mode());
        assertEquals(KEY, capturedParams.key());
        assertNull(capturedParams.keyFrom());
        assertNull(capturedParams.keyTo());
        assertNull(capturedParams.timeFrom());
        assertNull(capturedParams.timeTo());
    }

    @Test
    void test_session_fetch_mode() {
        // given
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        Mockito.when(facadeMock.getPrices(
                Mockito.eq(mockServerWebExchange), Mockito.any()
            ))
            .thenReturn(Flux.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getPrices(
                mockServerWebExchange,
                PriceSearchMode.SESSION_FETCH.getMode(), KEY, null, null, null, null
            ))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getPrices(
            Mockito.eq(mockServerWebExchange), argumentCaptor.capture()
        );

        assertEquals(1, argumentCaptor.getAllValues().size());
        final var capturedParams = argumentCaptor.getValue();
        assertEquals(PriceSearchMode.SESSION_FETCH, capturedParams.mode());
        assertEquals(KEY, capturedParams.key());
        assertNull(capturedParams.keyFrom());
        assertNull(capturedParams.keyTo());
        assertNull(capturedParams.timeFrom());
        assertNull(capturedParams.timeTo());
    }

    @Test
    void test_session_fetch_session_mode() {
        execute_key_with_time_range(PriceSearchMode.SESSION_FETCH_SESSION);
    }

    private void execute_key_with_time_range(PriceSearchMode mode) {
        // given
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        Mockito.when(facadeMock.getPrices(
                Mockito.eq(mockServerWebExchange), Mockito.any()
            ))
            .thenReturn(Flux.just(expectedResponse));

        final var timeTo = OffsetDateTime.now();
        final var timeFrom = timeTo.minusMinutes(1);
        StepVerifier
            // when
            .create(controller.getPrices(
                mockServerWebExchange,
                mode.getMode(), KEY, null, null, timeFrom, timeTo
            ))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getPrices(
            Mockito.eq(mockServerWebExchange), argumentCaptor.capture()
        );

        assertEquals(1, argumentCaptor.getAllValues().size());
        final var capturedParams = argumentCaptor.getValue();
        assertEquals(mode, capturedParams.mode());
        assertEquals(KEY, capturedParams.key());
        assertNull(capturedParams.keyFrom());
        assertNull(capturedParams.keyTo());
        assertEquals(timeFrom, capturedParams.timeFrom());
        assertEquals(timeTo, capturedParams.timeTo());
    }

    private void execute_with_time_range(PriceSearchMode mode) {
        // given
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        Mockito.when(facadeMock.getPrices(
                Mockito.eq(mockServerWebExchange), Mockito.any()
            ))
            .thenReturn(Flux.just(expectedResponse));

        final var timeTo = OffsetDateTime.now();
        final var timeFrom = timeTo.minusMinutes(1);
        StepVerifier
            // when
            .create(controller.getPrices(
                mockServerWebExchange,
                mode.getMode(), null, null, null, timeFrom, timeTo
            ))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getPrices(
            Mockito.eq(mockServerWebExchange), argumentCaptor.capture()
        );

        assertEquals(1, argumentCaptor.getAllValues().size());
        final var capturedParams = argumentCaptor.getValue();
        assertEquals(mode, capturedParams.mode());
        assertNull(capturedParams.key());
        assertNull(capturedParams.keyFrom());
        assertNull(capturedParams.keyTo());
        assertEquals(timeFrom, capturedParams.timeFrom());
        assertEquals(timeTo, capturedParams.timeTo());
    }

    @Test
    void test_session_find_sessions_mode() {
        execute_key_with_time_range(PriceSearchMode.SESSION_FIND_SESSIONS);
    }

    void execute_with_mode_param_only(PriceSearchMode mode) {
        // given
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        Mockito.when(facadeMock.getPrices(
                Mockito.eq(mockServerWebExchange), Mockito.any()
            ))
            .thenReturn(Flux.just(expectedResponse));

        StepVerifier
            // when
            .create(controller.getPrices(
                mockServerWebExchange,
                mode.getMode(), null, null, null, null, null
            ))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getPrices(
            Mockito.eq(mockServerWebExchange), argumentCaptor.capture()
        );

        assertEquals(1, argumentCaptor.getAllValues().size());
        final var capturedParams = argumentCaptor.getValue();
        assertEquals(mode, capturedParams.mode());
        assertNull(capturedParams.key());
        assertNull(capturedParams.keyFrom());
        assertNull(capturedParams.keyTo());
        assertNull(capturedParams.timeFrom());
        assertNull(capturedParams.timeTo());
    }

    @Test
    void test_all_mode() {
        execute_with_mode_param_only(PriceSearchMode.ALL);
    }

    @Test
    void test_backward_all_mode() {
        execute_with_mode_param_only(PriceSearchMode.BACKWARD_ALL);
    }

    @Test
    void test_fetch_mode() {
        execute_key_with_time_range(PriceSearchMode.FETCH);
    }

    @Test
    void test_fetch_all_mode() {
        execute_with_time_range(PriceSearchMode.FETCH_ALL);
    }

    @Test
    void test_backward_fetch_all_mode() {
        execute_with_time_range(PriceSearchMode.BACKWARD_FETCH_ALL);
    }

    @Test
    void test_backward_fetch_mode() {
        execute_key_with_time_range(PriceSearchMode.BACKWARD_FETCH);
    }

    private void execute_with_key_range_and_time_range(PriceSearchMode mode) {
        // given
        final var expectedResponse = createStockPricePredictionResponse()
            .getLeft();

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        Mockito.when(facadeMock.getPrices(
                Mockito.eq(mockServerWebExchange), Mockito.any()
            ))
            .thenReturn(Flux.just(expectedResponse));

        final var keyFrom = "A";
        final var keyTo = "C";
        final var timeTo = OffsetDateTime.now();
        final var timeFrom = timeTo.minusMinutes(1);

        StepVerifier
            // when
            .create(controller.getPrices(
                mockServerWebExchange,
                mode.getMode(), null, keyFrom, keyTo, timeFrom, timeTo
            ))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        Mockito.verify(facadeMock, Mockito.only()).getPrices(
            Mockito.eq(mockServerWebExchange), argumentCaptor.capture()
        );

        assertEquals(1, argumentCaptor.getAllValues().size());
        final var capturedParams = argumentCaptor.getValue();
        assertEquals(mode, capturedParams.mode());
        assertNull(capturedParams.key());
        assertEquals(keyFrom, capturedParams.keyFrom());
        assertEquals(keyTo, capturedParams.keyTo());
        assertEquals(timeFrom, capturedParams.timeFrom());
        assertEquals(timeTo, capturedParams.timeTo());
    }

    @Test
    void test_fetch_key_range_mode() {
        execute_with_key_range_and_time_range(PriceSearchMode.FETCH_KEY_RANGE);
    }

    @Test
    void test_backward_fetch_key_range_mode() {
        execute_with_key_range_and_time_range(PriceSearchMode.BACKWARD_FETCH_KEY_RANGE);
    }

    private static List<Arguments> createKeyNullArgSourceForSessionModes() {
        final var testNameFormat = "%s mode when key is null";
        final var toTime = OffsetDateTime.now();
        final var fromTime = toTime.minusMinutes(1);
        final var errorMsg = String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, "key");

        final var args = new ArrayList<Arguments>();

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FETCH),
            new GetPricesParams(
                PriceSearchMode.SESSION_FETCH,
                null, null, null, null, null
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FETCH_SESSION),
            new GetPricesParams(
                PriceSearchMode.SESSION_FETCH_SESSION,
                null, null, null, fromTime, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FIND_SESSIONS),
            new GetPricesParams(
                PriceSearchMode.SESSION_FIND_SESSIONS,
                null, null, null, fromTime, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS),
            new GetPricesParams(
                PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS,
                null, null, null, fromTime, toTime
            ),
            errorMsg
        ));

        return args;
    }

    private static List<Arguments> createFromTimeNullArgSourceForSessionModes() {
        final var testNameFormat = "%s mode when fromTime is null";
        final var toTime = OffsetDateTime.now();
        final var errorMsg = String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, StockMarketPricesController.TIME_FROM);

        final var args = new ArrayList<Arguments>();
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FETCH_SESSION),
            new GetPricesParams(
                PriceSearchMode.SESSION_FETCH_SESSION,
                KEY, null, null, null, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FIND_SESSIONS),
            new GetPricesParams(
                PriceSearchMode.SESSION_FIND_SESSIONS,
                KEY, null, null, null, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS),
            new GetPricesParams(
                PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS,
                KEY, null, null, null, toTime
            ),
            errorMsg
        ));

        return args;
    }

    private static List<Arguments> createTimeToNullArgSourceForSessionModes() {
        final var testNameFormat = "%s mode when timeTo is null";
        final var fromTime = OffsetDateTime.now();
        final var errorMsg = String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, StockMarketPricesController.TIME_TO);

        final var args = new ArrayList<Arguments>();
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FETCH_SESSION),
            new GetPricesParams(
                PriceSearchMode.SESSION_FETCH_SESSION,
                KEY, null, null, fromTime, null
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FIND_SESSIONS),
            new GetPricesParams(
                PriceSearchMode.SESSION_FIND_SESSIONS,
                KEY, null, null, fromTime, null
            ),
            errorMsg
        ));

        return args;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource({
        "createKeyNullArgSourceForSessionModes",
        "createFromTimeNullArgSourceForSessionModes",
        "createTimeToNullArgSourceForSessionModes",
        "createKeyNullArgSourceForTimeModes",
        "createFromTimeNullArgSourceForTimeModes",
        "createTimeToNullArgSourceForTimeModes",
        "createKeyFromNullArgSourceForTimeModes",
        "createKeyToNullArgSourceForTimeModes"
    })
    void test_bad_request(String name, GetPricesParams params, String expectedErrorMsg) {
        // given
        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        StepVerifier
            // when
            .create(controller.getPrices(
                mockServerWebExchange,
                params.mode().getMode(),
                params.key(), params.keyFrom(), params.keyTo(),
                params.timeFrom(), params.timeTo()
            ))
            // then
            .expectErrorSatisfies(throwable -> assertBadRequest(expectedErrorMsg, throwable))
            .verify();

        verifyNoFacadeInvocationForGetPrices(mockServerWebExchange);
    }

    private static void assertBadRequest(String expectedErrorMsg, Throwable throwable) {
        assertInstanceOf(ResponseStatusException.class, throwable);
        final var e = (ResponseStatusException) throwable;
        assertEquals(HttpStatus.BAD_REQUEST.value(), e.getStatusCode().value());
        assertEquals(expectedErrorMsg, e.getReason());
    }

    private static List<Arguments> createKeyNullArgSourceForTimeModes() {
        final var testNameFormat = "%s mode when key is null";
        final var toTime = OffsetDateTime.now();
        final var fromTime = toTime.minusMinutes(1);
        final var errorMsg = String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, StockMarketPricesController.KEY);

        final var args = new ArrayList<Arguments>();

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH),
            new GetPricesParams(
                PriceSearchMode.FETCH,
                null, null, null, fromTime, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH),
            new GetPricesParams(
                PriceSearchMode.BACKWARD_FETCH,
                null, null, null, fromTime, toTime
            ),
            errorMsg
        ));

        return args;
    }

    private static List<Arguments> createFromTimeNullArgSourceForTimeModes() {
        final var testNameFormat = "%s mode when fromTime is null";
        final var toTime = OffsetDateTime.now();
        final var errorMsg = String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, StockMarketPricesController.TIME_FROM);

        final var args = new ArrayList<Arguments>();
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH),
            new GetPricesParams(
                PriceSearchMode.FETCH,
                KEY, null, null, null, toTime
            ),
            errorMsg
        ));

        final var keyFrom = "A";
        final var keyTo = "C";

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH_KEY_RANGE),
            new GetPricesParams(
                PriceSearchMode.FETCH_KEY_RANGE,
                null, keyFrom, keyTo, null, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH_ALL),
            new GetPricesParams(
                PriceSearchMode.FETCH_ALL,
                null, null, null, null, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH_ALL),
            new GetPricesParams(
                PriceSearchMode.BACKWARD_FETCH_ALL,
                null, null, null, null, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH),
            new GetPricesParams(
                PriceSearchMode.BACKWARD_FETCH,
                KEY, null, null, null, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH_KEY_RANGE),
            new GetPricesParams(
                PriceSearchMode.BACKWARD_FETCH_KEY_RANGE,
                null, keyFrom, keyTo, null, toTime
            ),
            errorMsg
        ));

        return args;
    }

    private static List<Arguments> createTimeToNullArgSourceForTimeModes() {
        final var testNameFormat = "%s mode when timeTo is null";
        final var fromTime = OffsetDateTime.now();
        final var errorMsg = String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, StockMarketPricesController.TIME_TO);

        final var args = new ArrayList<Arguments>();
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH),
            new GetPricesParams(
                PriceSearchMode.FETCH,
                KEY, null, null, fromTime, null
            ),
            errorMsg
        ));

        final var keyFrom = "A";
        final var keyTo = "C";
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH_KEY_RANGE),
            new GetPricesParams(
                PriceSearchMode.FETCH_KEY_RANGE,
                null, keyFrom, keyTo, fromTime, null
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH_ALL),
            new GetPricesParams(
                PriceSearchMode.FETCH_ALL,
                null, null, null, fromTime, null
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH_ALL),
            new GetPricesParams(
                PriceSearchMode.BACKWARD_FETCH_ALL,
                null, null, null, fromTime, null
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH),
            new GetPricesParams(
                PriceSearchMode.BACKWARD_FETCH,
                KEY, null, null, fromTime, null
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH_KEY_RANGE),
            new GetPricesParams(
                PriceSearchMode.BACKWARD_FETCH_KEY_RANGE,
                null, keyFrom, keyTo, fromTime, null
            ),
            errorMsg
        ));

        return args;
    }

    private static List<Arguments> createKeyFromNullArgSourceForTimeModes() {
        final var testNameFormat = "%s mode when keyFrom is null";
        final var keyTo = "C";
        final var toTime = OffsetDateTime.now();
        final var fromTime = toTime.minusMinutes(1);
        final var errorMsg = String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, StockMarketPricesController.KEYF_ROM);

        final var args = new ArrayList<Arguments>();
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH_KEY_RANGE),
            new GetPricesParams(
                PriceSearchMode.FETCH_KEY_RANGE,
                null, null, keyTo, fromTime, null
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH_KEY_RANGE),
            new GetPricesParams(
                PriceSearchMode.BACKWARD_FETCH_KEY_RANGE,
                null, null, keyTo, fromTime, null
            ),
            errorMsg
        ));

        return args;
    }

    private static List<Arguments> createKeyToNullArgSourceForTimeModes() {
        final var testNameFormat = "%s mode when timeTo is null";
        final var keyFrom = "A";
        final var toTime = OffsetDateTime.now();
        final var fromTime = toTime.minusMinutes(1);
        final var errorMsg = String.format(PARAM_IS_NULL_ERR_MSG_FORMAT, StockMarketPricesController.KEY_TO);

        final var args = new ArrayList<Arguments>();
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH_KEY_RANGE),
            new GetPricesParams(
                PriceSearchMode.FETCH_KEY_RANGE,
                null, keyFrom, null, fromTime, toTime
            ),
            errorMsg
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH_KEY_RANGE),
            new GetPricesParams(
                PriceSearchMode.BACKWARD_FETCH_KEY_RANGE,
                null, keyFrom, null, fromTime, toTime
            ),
            errorMsg
        ));

        return args;
    }
}
