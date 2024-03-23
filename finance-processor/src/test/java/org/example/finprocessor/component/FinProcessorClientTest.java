package org.example.finprocessor.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finprocessor.api.ErrorCode;
import org.example.finprocessor.api.ErrorResponse;
import org.example.finprocessor.api.GetPredictionsParams;
import org.example.finprocessor.api.GetPricesParams;
import org.example.finprocessor.api.LossPredictionResponse;
import org.example.finprocessor.api.PredictionSearchMode;
import org.example.finprocessor.api.PriceSearchMode;
import org.example.finprocessor.api.StockPricePredictionResponse;
import org.example.finprocessor.api.StockPriceWindowResponse;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.exception.EntityNotFoundException;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.test.StockPriceFactory;
import org.example.finprocessor.test.StockPricePredictionDtoFactory;
import org.example.finprocessor.test.StockPricePredictionDtoUtil;
import org.example.finprocessor.test.StreamsMetadataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ExtendWith(MockitoExtension.class)
class FinProcessorClientTest {
    private static final String URL = StreamsMetadataFactory.NODE2_URL;
    private static final String VOO_TICKER = "VOO";
    private final static String MODE_PARAM_FORMAT = "mode=%s";

    private static final GetPredictionsParams SEARCH_MODE_ALL_PARAMS = new GetPredictionsParams(
        PredictionSearchMode.ALL, null, null, null
    );

    private final Converter<StockPricePredictionDto, StockPricePredictionResponse> toResponseConverter
        = new StockPricePredictionDtoToResponseConverter();

    @Mock
    private WebClient webClientMock;
    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestHeadersUriSpec headersUriSpecMock;
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpecMock;
    @Mock
    private WebClient.ResponseSpec responseSpecMock;
    @Mock
    private DataBuffer dataBufferMock;

    private FinProcessorClient finProcessorClient;

    @BeforeEach
    void setUp() {
        finProcessorClient = new FinProcessorClient(webClientMock);
    }

    private StockPricePredictionResponse createPredictionResponse() {
        final var vooETF = StockPriceFactory.vooAt20231212();
        final var pricePredictionDto = StockPricePredictionDtoFactory.of(
            vooETF, StockPricePredictionDtoUtil.closePlus1(vooETF)
        );

        return toResponseConverter.convert(pricePredictionDto);
    }

    private TopPredictionResponse createTopPredictionResponse(StockPricePredictionResponse response) {
        return new TopPredictionResponse(response.ticker(), response.pricePrediction());
    }

    private LossPredictionResponse createLossPredictionResponse(StockPricePredictionResponse response) {
        return new LossPredictionResponse(response.ticker(), response.pricePrediction());
    }

    @SuppressWarnings("unchecked")
    private void setupGetAndRetrieveAnyStringAndAnyObject() {
        Mockito.when(webClientMock.get()).thenReturn(headersUriSpecMock);
        Mockito.when(headersUriSpecMock.uri(Mockito.anyString(), Mockito.any(Object.class)))
            .thenReturn(requestBodyUriSpecMock);
        Mockito.when(requestBodyUriSpecMock.retrieve()).thenReturn(responseSpecMock);
    }

    @SuppressWarnings("unchecked")
    private void setupGetAndRetrieveAnyString() {
        Mockito.when(webClientMock.get()).thenReturn(headersUriSpecMock);
        Mockito.when(headersUriSpecMock.uri(Mockito.anyString()))
            .thenReturn(requestBodyUriSpecMock);
        Mockito.when(requestBodyUriSpecMock.retrieve()).thenReturn(responseSpecMock);
    }

    @Test
    void test_getPredictionByTicker() {
        // given
        final var pricePredictionResponse = createPredictionResponse();
        setupGetAndRetrieveAnyStringAndAnyObject();
        Mockito.when(responseSpecMock.bodyToMono(StockPricePredictionResponse.class))
            .thenReturn(Mono.just(pricePredictionResponse));

        StepVerifier
            // when
            .create(finProcessorClient.getPredictionByTicker(URL, VOO_TICKER))
            // then
            .expectNext(pricePredictionResponse)
            .verifyComplete();
    }

    private void setupWebClientResponseException(ErrorCode errorCode) {
        final var objectMapper = new ObjectMapper();
        final var errorResponse = new ErrorResponse(errorCode);

        final var httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        final byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        final var exception = new WebClientResponseException(
            httpStatus.value(),
            httpStatus.getReasonPhrase(),
            new HttpHeaders(),
            bodyBytes,
            StandardCharsets.UTF_8
        );
        final var mimeType = MimeType.valueOf(MediaType.APPLICATION_JSON_VALUE);
        final var httpMessageReader = new DecoderHttpMessageReader<>(new Jackson2JsonDecoder(objectMapper, mimeType));

        Mockito.when(dataBufferMock.asInputStream()).thenReturn(new ByteArrayInputStream(bodyBytes));
        exception.setBodyDecodeFunction(resolvableType -> httpMessageReader.getDecoder()
            .decode(dataBufferMock, resolvableType, mimeType, null)
        );
        Mockito.when(responseSpecMock.bodyToMono(StockPricePredictionResponse.class))
            .thenReturn(Mono.error(exception));
    }

    @Test
    void test_getPredictionByTicker_and_remote_host_returns_ticker_is_not_found_error() {
        // given
        setupGetAndRetrieveAnyStringAndAnyObject();

        final var tickerNotFoundErrorCode = ErrorCode.TICKER_NOT_FOUND;
        setupWebClientResponseException(tickerNotFoundErrorCode);

        StepVerifier
            // when
            .create(finProcessorClient.getPredictionByTicker(URL, VOO_TICKER))
            // then
            .expectErrorSatisfies(throwable -> {
                assertInstanceOf(EntityNotFoundException.class, throwable);
                final var e = (EntityNotFoundException) throwable;
                assertEquals(tickerNotFoundErrorCode, e.getErrorCode());
            })
            .verify();
    }

    @Test
    void test_getPredictionByTicker_and_remote_host_returns_unknown_error() {
        // given
        setupGetAndRetrieveAnyStringAndAnyObject();

        final var tickerNotFoundErrorCode = ErrorCode.UNKNOWN;
        setupWebClientResponseException(tickerNotFoundErrorCode);

        StepVerifier
            // when
            .create(finProcessorClient.getPredictionByTicker(URL, VOO_TICKER))
            // then
            .expectErrorSatisfies(throwable -> assertInstanceOf(WebClientResponseException.class, throwable))
            .verify();
    }

    @Test
    void test_getPredictionByTicker_and_remote_host_does_not_return_body() {
        // given
        setupGetAndRetrieveAnyStringAndAnyObject();

        final var httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        final var exception = new WebClientResponseException(
            httpStatus.value(),
            httpStatus.getReasonPhrase(),
            new HttpHeaders(),
            null,
            StandardCharsets.UTF_8
        );
        exception.setBodyDecodeFunction(resolvableType -> null);
        Mockito.when(responseSpecMock.bodyToMono(StockPricePredictionResponse.class))
            .thenReturn(Mono.error(exception));

        StepVerifier
            // when
            .create(finProcessorClient.getPredictionByTicker(URL, VOO_TICKER))
            // then
            .expectErrorMatches(throwable -> throwable instanceof WebClientResponseException)
            .verify();
    }

    @Test
    void test_getPredictionByTicker_and_fail_to_connect_to_remote_host() {
        // given
        setupGetAndRetrieveAnyStringAndAnyObject();

        Mockito.when(responseSpecMock.bodyToMono(StockPricePredictionResponse.class))
            .thenReturn(Mono.error(new ConnectException()));

        StepVerifier
            // when
            .create(finProcessorClient.getPredictionByTicker(URL, VOO_TICKER))
            // then
            .expectErrorMatches(throwable -> throwable instanceof ConnectException)
            .verify();
    }

    @SuppressWarnings("unchecked")
    private void setupGetPredictions(StockPricePredictionResponse response) {
        Mockito.when(webClientMock.get()).thenReturn(headersUriSpecMock);
        Mockito.when(headersUriSpecMock.uri(Mockito.anyString(), Mockito.any(Function.class)))
            .thenReturn(requestBodyUriSpecMock);
        Mockito.when(requestBodyUriSpecMock.retrieve()).thenReturn(responseSpecMock);

        Mockito.when(responseSpecMock.bodyToFlux(StockPricePredictionResponse.class))
            .thenReturn(Flux.just(response));
    }

    @Test
    void test_getPredictions() {
        // given
        final var pricePredictionResponse = createPredictionResponse();
        setupGetPredictions(pricePredictionResponse);

        StepVerifier
            // when
            .create(finProcessorClient.getPredictions(URL, SEARCH_MODE_ALL_PARAMS))
            // then
            .expectNext(pricePredictionResponse)
            .verifyComplete();
    }

    private static String withModeOnly(PredictionSearchMode mode) {
        return String.format(MODE_PARAM_FORMAT, mode.getMode());
    }

    private static String withFromA(PredictionSearchMode mode) {
        return String.format("%s&from=A", withModeOnly(mode));
    }

    private static String withFromAToC(PredictionSearchMode mode) {
        return String.format("%s&to=C", withFromA(mode));
    }

    private static String withToC(PredictionSearchMode mode) {
        return String.format("%s&to=C", withModeOnly(mode));
    }

    private static String withPrefixScanV() {
        return String.format("%s&prefix=V", withModeOnly(PredictionSearchMode.PREFIX_SCAN));
    }

    private static List<Arguments> createGetPredictionsUriFnArgSource() {
        final var from = "A";
        final var to = "C";
        final var prefix = "V";

        final var args = new ArrayList<Arguments>();

        args.add(Arguments.arguments(
            "All search mode",
            SEARCH_MODE_ALL_PARAMS,
            withModeOnly(PredictionSearchMode.ALL)
        ));

        args.add(Arguments.arguments(
            "All search mode with extra params that should be ignored",
            new GetPredictionsParams(PredictionSearchMode.ALL, from, to, prefix),
            withModeOnly(PredictionSearchMode.ALL)
        ));

        args.add(Arguments.arguments(
            "Range search mode",
            new GetPredictionsParams(PredictionSearchMode.RANGE, null, null, null),
            withModeOnly(PredictionSearchMode.RANGE)
        ));

        final var blank = " ";
        args.add(Arguments.arguments(
            "Range search mode with from and to params and blank values",
            new GetPredictionsParams(PredictionSearchMode.RANGE, blank, blank, null),
            withModeOnly(PredictionSearchMode.RANGE)
        ));

        args.add(Arguments.arguments(
            "Range search mode with from param",
            new GetPredictionsParams(PredictionSearchMode.RANGE, from, null, null),
            withFromA(PredictionSearchMode.RANGE)
        ));

        args.add(Arguments.arguments(
            "Range search mode with to param",
            new GetPredictionsParams(PredictionSearchMode.RANGE, null, to, null),
            withToC(PredictionSearchMode.RANGE)
        ));

        args.add(Arguments.arguments(
            "Range search mode with from and to params",
            new GetPredictionsParams(PredictionSearchMode.RANGE, from, to, null),
            withFromAToC(PredictionSearchMode.RANGE)
        ));

        args.add(Arguments.arguments(
            "Range search mode with prefix param that should be ignored",
            new GetPredictionsParams(PredictionSearchMode.RANGE, null, null, prefix),
            withModeOnly(PredictionSearchMode.RANGE)
        ));

        args.add(Arguments.arguments(
            "Reverse all search mode",
            new GetPredictionsParams(PredictionSearchMode.REVERSE_ALL, null, null, null),
            withModeOnly(PredictionSearchMode.REVERSE_ALL)
        ));

        args.add(Arguments.arguments(
            "Reverse all search mode with extra params that should be ignored",
            new GetPredictionsParams(PredictionSearchMode.REVERSE_ALL, from, to, prefix),
            withModeOnly(PredictionSearchMode.REVERSE_ALL)
        ));

        args.add(Arguments.arguments(
            "Reverse range search mode",
            new GetPredictionsParams(PredictionSearchMode.REVERSE_RANGE, null, null, null),
            withModeOnly(PredictionSearchMode.REVERSE_RANGE)
        ));

        args.add(Arguments.arguments(
            "Reverse range search mode with from and to params and blank values",
            new GetPredictionsParams(PredictionSearchMode.REVERSE_RANGE, blank, blank, null),
            withModeOnly(PredictionSearchMode.REVERSE_RANGE)
        ));

        args.add(Arguments.arguments(
            "Reverse range search mode with from param",
            new GetPredictionsParams(PredictionSearchMode.REVERSE_RANGE, from, null, null),
            withFromA(PredictionSearchMode.REVERSE_RANGE)
        ));

        args.add(Arguments.arguments(
            "Reverse range search mode with to param",
            new GetPredictionsParams(PredictionSearchMode.REVERSE_RANGE, null, to, null),
            withToC(PredictionSearchMode.REVERSE_RANGE)
        ));

        args.add(Arguments.arguments(
            "Reverse range search mode with from and to params",
            new GetPredictionsParams(PredictionSearchMode.REVERSE_RANGE, from, to, null),
            withFromAToC(PredictionSearchMode.REVERSE_RANGE)
        ));

        args.add(Arguments.arguments(
            "Reverse range search mode with prefix param that should be ignored",
            new GetPredictionsParams(PredictionSearchMode.REVERSE_RANGE, from, to, prefix),
            withFromAToC(PredictionSearchMode.REVERSE_RANGE)
        ));

        args.add(Arguments.arguments(
            "Prefix scan search mode with extra params that should be ignored",
            new GetPredictionsParams(PredictionSearchMode.PREFIX_SCAN, from, to, prefix),
            withPrefixScanV()
        ));

        return args;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("createGetPredictionsUriFnArgSource")
    void test_createGetPredictionsUriFn(String name, GetPredictionsParams params, String expectedQueryString) {
        // given
        final var uriFunction = FinProcessorClient.createGetPredictionsUriFn(params);
        final var uriComponentsBuilder = UriComponentsBuilder.fromUri(URI.create("/test"));

        // when
        final var uri = uriFunction.apply(uriComponentsBuilder);

        // then
        assertEquals(expectedQueryString, uri.getRawQuery());
    }

    @Test
    void test_getTopPredictions() {
        // given
        setupGetAndRetrieveAnyString();

        final var pricePredictionResponse = createPredictionResponse();
        final var topPredictionResponse = createTopPredictionResponse(pricePredictionResponse);
        Mockito.when(responseSpecMock.bodyToFlux(TopPredictionResponse.class))
            .thenReturn(Flux.just(topPredictionResponse));

        StepVerifier
            // when
            .create(finProcessorClient.getTopPredictions(URL))
            // then
            .expectNext(topPredictionResponse)
            .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void test_getLossPredictions() {
        // given
        final var pricePredictionResponse = createPredictionResponse();

        Mockito.when(webClientMock.get()).thenReturn(headersUriSpecMock);
        Mockito.when(headersUriSpecMock.uri(Mockito.anyString()))
            .thenReturn(requestBodyUriSpecMock);
        Mockito.when(requestBodyUriSpecMock.retrieve()).thenReturn(responseSpecMock);

        final var lossPredictionResponse = createLossPredictionResponse(pricePredictionResponse);
        Mockito.when(responseSpecMock.bodyToFlux(LossPredictionResponse.class))
            .thenReturn(Flux.just(lossPredictionResponse));

        StepVerifier
            // when
            .create(finProcessorClient.getLossPredictions(URL))
            // then
            .expectNext(lossPredictionResponse)
            .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void test_getPrices() {
        // given
        final var vooETF = StockPriceFactory.vooAt20231212();
        Mockito.when(webClientMock.get()).thenReturn(headersUriSpecMock);
        Mockito.when(headersUriSpecMock.uri(Mockito.anyString(), Mockito.any(Function.class)))
            .thenReturn(requestBodyUriSpecMock);
        Mockito.when(requestBodyUriSpecMock.retrieve()).thenReturn(responseSpecMock);

        final var now = OffsetDateTime.now();
        final var  windowStart = now.minusMinutes(1);
        final var params = new GetPricesParams(
            PriceSearchMode.ALL,
            null,
            "V",
            "X",
            windowStart,
            now
        );

        final var response = new StockPriceWindowResponse(
            null,
            null,
            null,
            List.of(vooETF)
        );

        Mockito.when(responseSpecMock.bodyToFlux(StockPriceWindowResponse.class))
            .thenReturn(Flux.just(response));

        StepVerifier
            // when
            .create(finProcessorClient.getPrices(URL, params))
            // then
            .expectNext(response)
            .verifyComplete();
    }

    private static List<Arguments> createGetPricesUriFnArgSource() {
        final var key = "VOO";
        final var keyFrom = "A";
        final var keyTo = "C";

        final var toTime = OffsetDateTime.now();
        final var fromTime = toTime.minusMinutes(1);

        final var testNameFormat = "%s mode";

        final var args = new ArrayList<Arguments>();
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FETCH),
            new GetPricesParams(PriceSearchMode.SESSION_FETCH, key, keyFrom, keyTo, fromTime, toTime),
            String.format("mode=%s&key=%s", PriceSearchMode.SESSION_FETCH.getMode(), key)
        ));

        final var keyWithTimeRangeFormat = "mode=%s&key=%s&timeFrom=%s&timeTo=%s";

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FETCH_SESSION),
            new GetPricesParams(PriceSearchMode.SESSION_FETCH_SESSION, key, keyFrom, keyTo, fromTime, toTime),
            String.format(
                keyWithTimeRangeFormat,
                PriceSearchMode.SESSION_FETCH_SESSION.getMode(), key, fromTime, toTime
            )
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_FIND_SESSIONS),
            new GetPricesParams(PriceSearchMode.SESSION_FIND_SESSIONS, key, keyFrom, keyTo, fromTime, toTime),
            String.format(
                keyWithTimeRangeFormat,
                PriceSearchMode.SESSION_FIND_SESSIONS.getMode(), key, fromTime, toTime
            )
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS),
            new GetPricesParams(PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS, key, keyFrom, keyTo, fromTime, toTime),
            String.format(
                keyWithTimeRangeFormat,
                PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS.getMode(), key, fromTime, toTime
            )
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.ALL),
            new GetPricesParams(PriceSearchMode.ALL, key, keyFrom, keyTo, fromTime, toTime),
            String.format(MODE_PARAM_FORMAT, PriceSearchMode.ALL.getMode())
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_ALL),
            new GetPricesParams(PriceSearchMode.BACKWARD_ALL, key, keyFrom, keyTo, fromTime, toTime),
            String.format(MODE_PARAM_FORMAT, PriceSearchMode.BACKWARD_ALL.getMode())
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH),
            new GetPricesParams(PriceSearchMode.FETCH, key, keyFrom, keyTo, fromTime, toTime),
            String.format(
                keyWithTimeRangeFormat,
                PriceSearchMode.FETCH.getMode(), key, fromTime, toTime
            )
        ));

        final var keyRangeWithTimeRangeFormat = "mode=%s&keyFrom=%s&keyTo=%s&timeFrom=%s&timeTo=%s";
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH_KEY_RANGE),
            new GetPricesParams(PriceSearchMode.FETCH_KEY_RANGE, key, keyFrom, keyTo, fromTime, toTime),
            String.format(
                keyRangeWithTimeRangeFormat,
                PriceSearchMode.FETCH_KEY_RANGE.getMode(), keyFrom, keyTo, fromTime, toTime
            )
        ));

        final var timeRangeFormat = "mode=%s&timeFrom=%s&timeTo=%s";
        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.FETCH_ALL),
            new GetPricesParams(PriceSearchMode.FETCH_ALL, key, keyFrom, keyTo, fromTime, toTime),
            String.format(
                timeRangeFormat,
                PriceSearchMode.FETCH_ALL.getMode(), fromTime, toTime
            )
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH_ALL),
            new GetPricesParams(PriceSearchMode.BACKWARD_FETCH_ALL, key, keyFrom, keyTo, fromTime, toTime),
            String.format(
                timeRangeFormat,
                PriceSearchMode.BACKWARD_FETCH_ALL.getMode(), fromTime, toTime
            )
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH),
            new GetPricesParams(PriceSearchMode.BACKWARD_FETCH, key, keyFrom, keyTo, fromTime, toTime),
            String.format(
                keyWithTimeRangeFormat,
                PriceSearchMode.BACKWARD_FETCH.getMode(), key, fromTime, toTime
            )
        ));

        args.add(Arguments.arguments(
            String.format(testNameFormat, PriceSearchMode.BACKWARD_FETCH_KEY_RANGE),
            new GetPricesParams(PriceSearchMode.BACKWARD_FETCH_KEY_RANGE, key, keyFrom, keyTo, fromTime, toTime),
            String.format(
                keyRangeWithTimeRangeFormat,
                PriceSearchMode.BACKWARD_FETCH_KEY_RANGE.getMode(), keyFrom, keyTo, fromTime, toTime
            )
        ));

        return args;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("createGetPricesUriFnArgSource")
    void test_createGetPricesUriFn(String name, GetPricesParams params, String expectedQueryString) {
        // given
        final var uriFunction = finProcessorClient.createGetPricesUriFn(params);
        final var uriComponentsBuilder = UriComponentsBuilder.fromUri(URI.create("/test"));

        // when
        final var uri = uriFunction.apply(uriComponentsBuilder);

        // then
        assertEquals(expectedQueryString, uri.getRawQuery());
    }
}
