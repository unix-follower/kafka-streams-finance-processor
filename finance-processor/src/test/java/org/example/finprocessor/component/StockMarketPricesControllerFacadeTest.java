package org.example.finprocessor.component;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.state.ReadOnlySessionStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.example.finprocessor.api.GetPricesParams;
import org.example.finprocessor.api.PriceSearchMode;
import org.example.finprocessor.api.StockPriceWindowResponse;
import org.example.finprocessor.stockmarket.api.StockPrice;
import org.example.finprocessor.test.AppPropertiesFactory;
import org.example.finprocessor.test.InMemoryKeyValueIterator;
import org.example.finprocessor.test.InMemoryWindowStoreIterator;
import org.example.finprocessor.test.ServerWebExchangeFactory;
import org.example.finprocessor.test.StockPriceFactory;
import org.example.finprocessor.test.StockPriceWindowResponseUtil;
import org.example.finprocessor.test.StreamsMetadataFactory;
import org.example.finprocessor.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("resource")
@ExtendWith(MockitoExtension.class)
class StockMarketPricesControllerFacadeTest {
    private static final String KEY = "VOO";

    private final AppPropertiesFactory appPropertiesFactory = new AppPropertiesFactory();

    @Mock
    private StreamsBuilderFactoryBean factoryBeanMock;
    @Mock
    private KafkaStreams kafkaStreamsMock;
    @Mock
    private ReadOnlySessionStore<String, List<StockPrice>> stockPriceSessionStoreMock;
    @Mock
    private ReadOnlyWindowStore<String, ValueAndTimestamp<List<StockPrice>>> stockPriceWindowStoreMock;
    @Mock
    private FinanceProcessorClient financeProcessorClientMock;
    private StockMarketPricesControllerFacade facade;

    @BeforeEach
    void setUp() {
        final var appProperties = appPropertiesFactory.create();
        facade = new StockMarketPricesControllerFacade(
            factoryBeanMock, appProperties, financeProcessorClientMock
        );
    }

    private void setupKafkaStreams() {
        Mockito.when(factoryBeanMock.getKafkaStreams()).thenReturn(kafkaStreamsMock);
    }

    private void setupStreamsMetadataForStore(String storeName) {
        Mockito.when(kafkaStreamsMock.streamsMetadataForStore(storeName))
            .thenReturn(StreamsMetadataFactory.clusterOf3Nodes());
    }

    private void setupStateStore(ReadOnlySessionStore<String, List<StockPrice>> store) {
        Mockito.when(kafkaStreamsMock.store(Mockito.any())).thenReturn(store);
    }

    private void setupStateStore(ReadOnlyWindowStore<String, ValueAndTimestamp<List<StockPrice>>> store) {
        Mockito.when(kafkaStreamsMock.store(Mockito.any())).thenReturn(store);
    }

    @Test
    void test_all_mode_but_window_mode_is_set_to_session_then_skip() {
        // given
        setupKafkaStreams();

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.ALL,
            null,
            null,
            null,
            windowStart,
            windowEnd
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextCount(0)
            .verifyComplete();

        Mockito.verify(factoryBeanMock, Mockito.only()).getKafkaStreams();
        Mockito.verify(kafkaStreamsMock, Mockito.never()).store(Mockito.any());
        Mockito.verify(stockPriceSessionStoreMock, Mockito.never()).fetch(Mockito.any());
        Mockito.verify(stockPriceWindowStoreMock, Mockito.never()).all();
    }

    private void verifyGetKafkaStreamsCalledTwice() {
        Mockito.verify(factoryBeanMock, Mockito.times(2)).getKafkaStreams();
    }

    private void verifyStoreObtained() {
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce()).store(Mockito.any());
    }

    @Test
    void test_session_fetch_mode() {
        // given
        setupKafkaStreams();
        setupStateStore(stockPriceSessionStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);

        final var sessionWindow = new SessionWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, sessionWindow);

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);

        final var keyValues = List.of(
            new KeyValue<>(windowed, prices)
        );
        Mockito.when(stockPriceSessionStoreMock.fetch(KEY))
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.SESSION_FETCH,
            KEY,
            null,
            null,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            null,
            windowStart,
            windowEnd,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceSessionStoreMock, Mockito.only()).fetch(KEY);
    }

    @Test
    void test_fetch_session_mode() {
        // given
        setupKafkaStreams();
        setupStateStore(stockPriceSessionStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);

        final long windowStartMs = windowStart.toInstant().toEpochMilli();
        final long windowEndMs = windowEnd.toInstant().toEpochMilli();

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);

        Mockito.when(stockPriceSessionStoreMock.fetchSession(KEY, windowStartMs, windowEndMs))
            .thenReturn(prices);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.SESSION_FETCH_SESSION,
            KEY,
            null,
            null,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            null,
            null,
            null,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNext(expectedResponse)
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceSessionStoreMock, Mockito.only()).fetchSession(KEY, windowStartMs, windowEndMs);
    }

    @Test
    void test_find_sessions_mode() {
        // given
        setupKafkaStreams();
        setupStateStore(stockPriceSessionStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);

        final long windowStartMs = windowStart.toInstant().toEpochMilli();
        final long windowEndMs = windowEnd.toInstant().toEpochMilli();

        final var sessionWindow = new SessionWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, sessionWindow);

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(windowed, prices)
        );

        Mockito.when(stockPriceSessionStoreMock.findSessions(KEY, windowStartMs, windowEndMs))
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.SESSION_FIND_SESSIONS,
            KEY,
            null,
            null,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            null,
            windowStart,
            windowEnd,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceSessionStoreMock, Mockito.only()).findSessions(KEY, windowStartMs, windowEndMs);
    }

    @Test
    void test_backward_find_sessions_mode() {
        // given
        setupKafkaStreams();
        setupStateStore(stockPriceSessionStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);

        final long windowStartMs = windowStart.toInstant().toEpochMilli();
        final long windowEndMs = windowEnd.toInstant().toEpochMilli();

        final var sessionWindow = new SessionWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, sessionWindow);

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(windowed, prices)
        );

        Mockito.when(stockPriceSessionStoreMock.backwardFindSessions(KEY, windowStartMs, windowEndMs))
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.SESSION_BACKWARD_FIND_SESSIONS,
            KEY,
            null,
            null,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            null,
            windowStart,
            windowEnd,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceSessionStoreMock, Mockito.only()).backwardFindSessions(KEY, windowStartMs, windowEndMs);
    }

    private StockMarketPricesControllerFacade createTimeWindowFacade() {
        appPropertiesFactory.setStockMarketStreamPropertiesSupplier(
            AppPropertiesFactory.PROPS_SUPPLIER_WITH_TIME_WINDOW_MODE
        );
        final var appProperties = appPropertiesFactory.create();
        return new StockMarketPricesControllerFacade(
            factoryBeanMock, appProperties, financeProcessorClientMock
        );
    }

    @Test
    void test_all_mode() {
        // given
        facade = createTimeWindowFacade();

        setupKafkaStreams();
        setupStateStore(stockPriceWindowStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);
        final var eventTime = windowEnd.minusSeconds(5);

        final var timeWindow = new TimeWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, timeWindow);

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(windowed, ValueAndTimestamp.make(prices, eventTime.toInstant().toEpochMilli()))
        );

        final var iterator = new InMemoryKeyValueIterator<>(keyValues);
        Mockito.when(stockPriceWindowStoreMock.all())
            .thenReturn(iterator);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.ALL,
            null,
            null,
            null,
            null,
            null
        );

        final var expectedResponse = new StockPriceWindowResponse(
            eventTime,
            windowStart,
            windowEnd,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceWindowStoreMock, Mockito.only()).all();
    }

    @Test
    void test_backward_all_mode() {
        // given
        facade = createTimeWindowFacade();

        setupKafkaStreams();
        setupStateStore(stockPriceWindowStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);
        final var eventTime = windowEnd.minusSeconds(5);

        final var timeWindow = new TimeWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, timeWindow);

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(windowed, ValueAndTimestamp.make(prices, eventTime.toInstant().toEpochMilli()))
        );

        final var iterator = new InMemoryKeyValueIterator<>(keyValues);
        Mockito.when(stockPriceWindowStoreMock.backwardAll())
            .thenReturn(iterator);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.BACKWARD_ALL,
            null,
            null,
            null,
            null,
            null
        );

        final var expectedResponse = new StockPriceWindowResponse(
            eventTime,
            windowStart,
            windowEnd,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceWindowStoreMock, Mockito.only()).backwardAll();
    }

    @Test
    void test_fetch_mode() {
        // given
        facade = createTimeWindowFacade();

        setupKafkaStreams();
        setupStateStore(stockPriceWindowStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);
        final var eventTime = windowEnd.minusSeconds(5);

        final var windowStartInstant = windowStart.toInstant();
        final var windowEndInstant = windowEnd.toInstant();

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(
                windowStartInstant.toEpochMilli(),
                ValueAndTimestamp.make(prices, eventTime.toInstant().toEpochMilli())
            )
        );

        Mockito.when(stockPriceWindowStoreMock.fetch(KEY, windowStartInstant, windowEndInstant))
            .thenReturn(new InMemoryWindowStoreIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.FETCH,
            KEY,
            null,
            null,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            eventTime,
            null,
            null,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceWindowStoreMock, Mockito.only()).fetch(
            KEY, windowStartInstant, windowEndInstant
        );
    }

    @Test
    void test_fetch_key_range_mode() {
        // given
        facade = createTimeWindowFacade();

        setupKafkaStreams();
        setupStateStore(stockPriceWindowStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);
        final var eventTime = windowEnd.minusSeconds(5);

        final var windowStartInstant = windowStart.toInstant();
        final var windowEndInstant = windowEnd.toInstant();

        final var timeWindow = new TimeWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, timeWindow);

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(
                windowed,
                ValueAndTimestamp.make(prices, eventTime.toInstant().toEpochMilli())
            )
        );

        final var keyFrom = "T";
        final var keyTo = "X";
        Mockito.when(stockPriceWindowStoreMock.fetch(keyFrom, keyTo, windowStartInstant, windowEndInstant))
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.FETCH_KEY_RANGE,
            null,
            keyFrom,
            keyTo,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            eventTime,
            windowStart,
            windowEnd,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceWindowStoreMock, Mockito.only()).fetch(
            keyFrom, keyTo, windowStartInstant, windowEndInstant
        );
    }

    @Test
    void test_fetch_all_mode() {
        // given
        facade = createTimeWindowFacade();

        setupKafkaStreams();
        setupStateStore(stockPriceWindowStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);
        final var eventTime = windowEnd.minusSeconds(5);

        final var windowStartInstant = windowStart.toInstant();
        final var windowEndInstant = windowEnd.toInstant();

        final var timeWindow = new TimeWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, timeWindow);

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(
                windowed,
                ValueAndTimestamp.make(prices, eventTime.toInstant().toEpochMilli())
            )
        );

        Mockito.when(stockPriceWindowStoreMock.fetchAll(windowStartInstant, windowEndInstant))
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.FETCH_ALL,
            null,
            null,
            null,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            eventTime,
            windowStart,
            windowEnd,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceWindowStoreMock, Mockito.only()).fetchAll(
            windowStartInstant, windowEndInstant
        );
    }

    @Test
    void test_backward_fetch_all_mode() {
        // given
        facade = createTimeWindowFacade();

        setupKafkaStreams();
        setupStateStore(stockPriceWindowStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);
        final var eventTime = windowEnd.minusSeconds(5);

        final var windowStartInstant = windowStart.toInstant();
        final var windowEndInstant = windowEnd.toInstant();

        final var timeWindow = new TimeWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, timeWindow);

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(
                windowed,
                ValueAndTimestamp.make(prices, eventTime.toInstant().toEpochMilli())
            )
        );

        Mockito.when(stockPriceWindowStoreMock.backwardFetchAll(windowStartInstant, windowEndInstant))
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.BACKWARD_FETCH_ALL,
            null,
            null,
            null,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            eventTime,
            windowStart,
            windowEnd,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceWindowStoreMock, Mockito.only()).backwardFetchAll(
            windowStartInstant, windowEndInstant
        );
    }

    @Test
    void test_backward_fetch_mode() {
        // given
        facade = createTimeWindowFacade();

        setupKafkaStreams();
        setupStateStore(stockPriceWindowStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);
        final var eventTime = windowEnd.minusSeconds(5);

        final var windowStartInstant = windowStart.toInstant();
        final var windowEndInstant = windowEnd.toInstant();

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(
                windowStartInstant.toEpochMilli(),
                ValueAndTimestamp.make(prices, eventTime.toInstant().toEpochMilli())
            )
        );

        Mockito.when(stockPriceWindowStoreMock.backwardFetch(KEY, windowStartInstant, windowEndInstant))
            .thenReturn(new InMemoryWindowStoreIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.BACKWARD_FETCH,
            KEY,
            null,
            null,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            eventTime,
            null,
            null,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceWindowStoreMock, Mockito.only()).backwardFetch(
            KEY, windowStartInstant, windowEndInstant
        );
    }

    @Test
    void test_backward_fetch_key_range_mode() {
        // given
        facade = createTimeWindowFacade();

        setupKafkaStreams();
        setupStateStore(stockPriceWindowStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);
        final var eventTime = windowEnd.minusSeconds(5);

        final var windowStartInstant = windowStart.toInstant();
        final var windowEndInstant = windowEnd.toInstant();

        final var timeWindow = new TimeWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, timeWindow);

        final var vooETF = StockPriceFactory.vooAt20231212();
        final var prices = List.of(vooETF);
        final var keyValues = List.of(
            new KeyValue<>(
                windowed,
                ValueAndTimestamp.make(prices, eventTime.toInstant().toEpochMilli())
            )
        );

        final var keyFrom = "T";
        final var keyTo = "X";
        Mockito.when(stockPriceWindowStoreMock.backwardFetch(keyFrom, keyTo, windowStartInstant, windowEndInstant))
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.BACKWARD_FETCH_KEY_RANGE,
            null,
            keyFrom,
            keyTo,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            eventTime,
            windowStart,
            windowEnd,
            prices
        );

        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .expectNextMatches(actual ->
                StockPriceWindowResponseUtil.equalsWithApproximateWindows(expectedResponse, actual)
            )
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceWindowStoreMock, Mockito.only()).backwardFetch(
            keyFrom, keyTo, windowStartInstant, windowEndInstant
        );
    }

    private void setupNodeGetPricesResponse(String nodeUrl, Flux<StockPriceWindowResponse> responseFlux) {
        Mockito.when(financeProcessorClientMock.getPrices(Mockito.eq(nodeUrl), Mockito.any()))
            .thenReturn(responseFlux);
    }

    @Test
    void test_fetch_key_range_mode_and_remote_hosts_return_results() {
        // given
        facade = createTimeWindowFacade();

        setupKafkaStreams();
        setupStreamsMetadataForStore(Constants.PREDICTIONS_TIME_WINDOWED_STORE);
        setupStateStore(stockPriceWindowStoreMock);

        final var windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        final var windowStart = windowEnd.minusMinutes(1);
        final var eventTime = windowEnd.minusSeconds(5);

        final var nvdaShare = StockPriceFactory.nvdaAt20240221();
        final var expectedRemoteNVDAResponse = new StockPriceWindowResponse(
            eventTime,
            windowStart,
            windowEnd,
            List.of(nvdaShare)
        );
        setupNodeGetPricesResponse(StreamsMetadataFactory.NODE2_URL, Flux.just(expectedRemoteNVDAResponse));

        final var maShare = StockPriceFactory.maAt20240221();
        final var expectedRemoteMAResponse = new StockPriceWindowResponse(
            eventTime,
            windowStart,
            windowEnd,
            List.of(maShare)
        );
        setupNodeGetPricesResponse(StreamsMetadataFactory.NODE3_URL, Flux.just(expectedRemoteMAResponse));

        final var windowStartInstant = windowStart.toInstant();
        final var windowEndInstant = windowEnd.toInstant();

        final var timeWindow = new TimeWindow(
            windowStart.toInstant().toEpochMilli(),
            windowEnd.toInstant().toEpochMilli()
        );
        final var windowed = new Windowed<>(KEY, timeWindow);

        final var aaplShare = StockPriceFactory.aaplAt20240221();

        final var prices = List.of(aaplShare);
        final var keyValues = List.of(
            new KeyValue<>(
                windowed,
                ValueAndTimestamp.make(prices, eventTime.toInstant().toEpochMilli())
            )
        );

        final var keyFrom = "A";
        final var keyTo = "M";
        Mockito.when(stockPriceWindowStoreMock.fetch(keyFrom, keyTo, windowStartInstant, windowEndInstant))
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/prices");

        final var params = new GetPricesParams(
            PriceSearchMode.FETCH_KEY_RANGE,
            null,
            keyFrom,
            keyTo,
            windowStart,
            windowEnd
        );

        final var expectedResponse = new StockPriceWindowResponse(
            eventTime,
            windowStart,
            windowEnd,
            prices
        );

        final var responses = new HashSet<StockPriceWindowResponse>();
        StepVerifier
            // when
            .create(facade.getPrices(mockServerWebExchange, params))
            // then
            .consumeNextWith(responses::add)
            .consumeNextWith(responses::add)
            .consumeNextWith(responses::add)
            .verifyComplete();

        final var expectedResponses = List.of(
            expectedResponse,
            expectedRemoteNVDAResponse,
            expectedRemoteMAResponse
        );
        final var expectedPriceSet = expectedResponses.stream()
            .flatMap(response -> response.prices().stream())
            .collect(Collectors.toSet());

        assertEquals(expectedPriceSet.size(), responses.size());
        final var actualSet = responses.stream()
            .flatMap(response -> response.prices().stream())
            .collect(Collectors.toSet());
        assertTrue(expectedPriceSet.containsAll(actualSet));

        responses.forEach(response -> {
            assertTrue(StockPriceWindowResponseUtil.isLeftApproximatelyEqual(
                eventTime, response.eventTime()
            ));
            assertTrue(StockPriceWindowResponseUtil.isLeftApproximatelyEqual(
                windowStart, response.windowStart()
            ));
            assertTrue(StockPriceWindowResponseUtil.isLeftApproximatelyEqual(
                windowEnd, response.windowEnd()
            ));
        });

        verifyGetKafkaStreamsCalledTwice();
        verifyStoreObtained();
        Mockito.verify(stockPriceWindowStoreMock, Mockito.only()).fetch(
            keyFrom, keyTo, windowStartInstant, windowEndInstant
        );
        Mockito.verify(financeProcessorClientMock, Mockito.atMostOnce()).getPrices(
            Mockito.eq(StreamsMetadataFactory.NODE2_URL), Mockito.any()
        );
        Mockito.verify(financeProcessorClientMock, Mockito.atMostOnce()).getPrices(
            Mockito.eq(StreamsMetadataFactory.NODE3_URL), Mockito.any()
        );
    }
}
