package org.example.finprocessor.component;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.example.finprocessor.api.GetPredictionsParams;
import org.example.finprocessor.api.LossPredictionResponse;
import org.example.finprocessor.api.SearchMode;
import org.example.finprocessor.api.StockPricePredictionResponse;
import org.example.finprocessor.api.TopPredictionResponse;
import org.example.finprocessor.exception.EntityNotFoundException;
import org.example.finprocessor.api.ErrorCode;
import org.example.finprocessor.stockmarket.api.StockPricePredictionDto;
import org.example.finprocessor.test.InMemoryKeyValueIterator;
import org.example.finprocessor.test.ServerWebExchangeFactory;
import org.example.finprocessor.test.StockPriceFactory;
import org.example.finprocessor.test.StockPricePredictionDtoFactory;
import org.example.finprocessor.test.StockPricePredictionDtoUtil;
import org.example.finprocessor.test.StreamsMetadataFactory;
import org.example.finprocessor.test.TopPredictionFactory;
import org.example.finprocessor.util.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.converter.Converter;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

@ExtendWith(MockitoExtension.class)
class StockMarketControllerFacadeTest {
    private static final GetPredictionsParams SEARCH_MODE_GET_PREDICTIONS_PARAMS
        = new GetPredictionsParams(SearchMode.ALL, null, null, null);

    private final Converter<StockPricePredictionDto, StockPricePredictionResponse> toResponseConverter
        = new StockPricePredictionDtoToResponseConverter();

    @Mock
    private StreamsBuilderFactoryBean factoryBeanMock;
    @Mock
    private KafkaStreams kafkaStreamsMock;
    @Mock
    private KeyQueryMetadata keyQueryMetadataMock;
    @Mock
    private ReadOnlyKeyValueStore<String, StockPricePredictionDto> stockPricePredictionStoreMock;
    @Mock
    private ReadOnlyKeyValueStore<String, SortedSet<TopPredictionResponse>> topPredictionsStoreMock;
    @Mock
    private ReadOnlyKeyValueStore<String, String> lossPredictionsStoreMock;

    @Mock
    private FinanceProcessorClient financeProcessorClientMock;

    private StockMarketControllerFacade facade;

    @BeforeEach
    void setUp() {
        facade = new StockMarketControllerFacade(factoryBeanMock, financeProcessorClientMock);

        setupKafkaStreams();
    }

    private void setupKafkaStreams() {
        Mockito.when(factoryBeanMock.getKafkaStreams()).thenReturn(kafkaStreamsMock);
    }

    private void setupNodeEmptyResponse(String nodeUrl, GetPredictionsParams params) {
        Mockito.when(financeProcessorClientMock.getPredictions(nodeUrl, params))
            .thenReturn(Flux.empty());
    }

    private void setup2ndNodeResponseForVOOTicker(Mono<StockPricePredictionResponse> responseMono) {
        Mockito.when(
                financeProcessorClientMock.getPredictionByTicker(StreamsMetadataFactory.NODE2_URL, "VOO")
            )
            .thenReturn(responseMono);
    }

    private void setupNodeTopPredictionResponse(String nodeUrl, Flux<TopPredictionResponse> responseFlux) {
        Mockito.when(financeProcessorClientMock.getTopPredictions(nodeUrl))
            .thenReturn(responseFlux);
    }

    private void setupNodeLossPredictionResponse(String nodeUrl, Flux<LossPredictionResponse> responseFlux) {
        Mockito.when(financeProcessorClientMock.getLossPredictions(nodeUrl))
            .thenReturn(responseFlux);
    }

    @SuppressWarnings("SameParameterValue")
    private void setupStreamsMetadataForStore(String storeName) {
        Mockito.when(kafkaStreamsMock.streamsMetadataForStore(storeName))
            .thenReturn(StreamsMetadataFactory.clusterOf3Nodes());
    }

    private void setupStateStore(ReadOnlyKeyValueStore<String, ?> store) {
        Mockito.when(kafkaStreamsMock.store(Mockito.any()))
            .thenReturn(store);
    }

    private KeyValueIterator<String, StockPricePredictionDto> createStockPricePredictionInMemoryKeyValueIterator(
        List<StockPricePredictionDto> pricePredictions
    ) {
        final var keyValues = pricePredictions.stream()
            .map(predictionDto -> new KeyValue<>(
                predictionDto.ticker(),
                predictionDto
            ))
            .toList();
        return new InMemoryKeyValueIterator<>(keyValues);
    }

    private void setupAllStoreIterator(List<StockPricePredictionDto> pricePredictions) {
        Mockito.when(stockPricePredictionStoreMock.all())
            .thenReturn(createStockPricePredictionInMemoryKeyValueIterator(pricePredictions));
    }

    private void verifyAllIteratorCallOnce() {
        Mockito.verify(stockPricePredictionStoreMock, Mockito.only()).all();
    }

    private Pair<StockPricePredictionDto, StockPricePredictionResponse> createVOOSingleItem() {
        final var vooETF = StockPriceFactory.vooAt20231212();
        final var pricePredictionDto = StockPricePredictionDtoFactory.of(
            vooETF, StockPricePredictionDtoUtil.closePlus1(vooETF)
        );

        final var expectedPredictionResponse = toResponseConverter.convert(pricePredictionDto);
        return Pair.of(pricePredictionDto, expectedPredictionResponse);
    }

    private Pair<StockPricePredictionDto, StockPricePredictionResponse> createSPYSingleItem() {
        final var spyETF = StockPriceFactory.spyAt20231212();
        final var pricePredictionDto = StockPricePredictionDtoFactory.of(
            spyETF, StockPricePredictionDtoUtil.closePlus1(spyETF)
        );

        final var expectedPredictionResponse = toResponseConverter.convert(pricePredictionDto);
        return Pair.of(pricePredictionDto, expectedPredictionResponse);
    }

    private Pair<StockPricePredictionDto, StockPricePredictionResponse> createGOOGLSingleItem() {
        final var googlShare = StockPriceFactory.googlAt20231212();
        final var pricePredictionDto = StockPricePredictionDtoFactory.of(
            googlShare, StockPricePredictionDtoUtil.closePlus1(googlShare)
        );

        final var expectedPredictionResponse = toResponseConverter.convert(pricePredictionDto);
        return Pair.of(pricePredictionDto, expectedPredictionResponse);
    }

    private Pair<StockPricePredictionDto, StockPricePredictionResponse> createVTSAXSingleItem() {
        final var vtsaxFund = StockPriceFactory.vtsaxAt20231212();
        final var pricePredictionDto = StockPricePredictionDtoFactory.of(
            vtsaxFund, StockPricePredictionDtoUtil.closePlus1(vtsaxFund)
        );

        final var expectedPredictionResponse = toResponseConverter.convert(pricePredictionDto);
        return Pair.of(pricePredictionDto, expectedPredictionResponse);
    }

    private void execute_test_getPredictions_with_params_and_remote_hosts_return_nothing(
        GetPredictionsParams params,
        Consumer<List<StockPricePredictionDto>> setupStoreIteratorConsumer,
        Runnable storeIteratorVerifyFn
    ) {
        // given
        final var vooPair = createVOOSingleItem();
        final var vooPricePredictionDto = vooPair.getLeft();
        final var expectedVOOResponse = vooPair.getRight();
        setupStoreIteratorConsumer.accept(List.of(vooPricePredictionDto));

        final var mockServerWebExchange = ServerWebExchangeFactory
            .createGetServerRequest("/api/v1/predictions");

        StepVerifier
            // when
            .create(facade.getPredictions(mockServerWebExchange, params))
            // then
            .expectNext(expectedVOOResponse)
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStreamsMetadataForPredictionsStoreFetchedOnce();
        verifyClusterNodesCalledOnce(params);
        verifyStoreGotOnce();
        storeIteratorVerifyFn.run();
    }

    private void verifyStoreGotOnce() {
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce()).store(Mockito.any());
    }

    private void verifyStoreNeverObtained() {
        Mockito.verify(kafkaStreamsMock, Mockito.never()).store(Mockito.any());
    }

    private void verifyStreamsMetadataForPredictionsStoreFetchedOnce() {
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce())
            .streamsMetadataForStore(Constants.PREDICTIONS_STORE);
    }

    private void verifyGetKafkaStreamsCalledOnce() {
        // 1 time to get metadata
        Mockito.verify(factoryBeanMock, Mockito.times(1)).getKafkaStreams();
    }

    private void verifyGetKafkaStreamsCalledTwice() {
        // 1 time to get metadata and 2nd time to get a state store
        Mockito.verify(factoryBeanMock, Mockito.times(2)).getKafkaStreams();
    }

    private void verifyClusterNodesCalledOnce(GetPredictionsParams params) {
        Mockito.verify(financeProcessorClientMock, Mockito.atMostOnce())
            .getPredictions(StreamsMetadataFactory.NODE2_URL, params);

        Mockito.verify(financeProcessorClientMock, Mockito.atMostOnce())
            .getPredictions(StreamsMetadataFactory.NODE3_URL, params);
    }

    private void verifyClusterNodesCalledOnce() {
        Mockito.verify(financeProcessorClientMock, Mockito.atMostOnce())
            .getLossPredictions(StreamsMetadataFactory.NODE2_URL);

        Mockito.verify(financeProcessorClientMock, Mockito.atMostOnce())
            .getLossPredictions(StreamsMetadataFactory.NODE3_URL);
    }

    private void verifyClusterNodesNeverCalledForGetTopPredictions() {
        Mockito.verify(financeProcessorClientMock, Mockito.never())
            .getTopPredictions(StreamsMetadataFactory.NODE2_URL);

        Mockito.verify(financeProcessorClientMock, Mockito.never())
            .getTopPredictions(StreamsMetadataFactory.NODE3_URL);
    }

    private void verifyCluster2ndNodeCalledForGetTopPredictions() {
        Mockito.verify(financeProcessorClientMock, Mockito.atMostOnce())
            .getTopPredictions(StreamsMetadataFactory.NODE2_URL);

        Mockito.verify(financeProcessorClientMock, Mockito.never())
            .getTopPredictions(StreamsMetadataFactory.NODE3_URL);
    }

    private void verifyClusterNodesNeverCalledForGetPredictionByTicker() {
        Mockito.verify(financeProcessorClientMock, Mockito.never())
            .getPredictionByTicker(
                Mockito.eq(StreamsMetadataFactory.NODE2_URL),
                Mockito.any()
            );

        Mockito.verify(financeProcessorClientMock, Mockito.never())
            .getPredictionByTicker(
                Mockito.eq(StreamsMetadataFactory.NODE3_URL),
                Mockito.any()
            );
    }

    private void verifyCluster2ndNodeCalledForTickerVOO() {
        Mockito.verify(financeProcessorClientMock, Mockito.atMostOnce())
            .getPredictionByTicker(StreamsMetadataFactory.NODE2_URL, "VOO");

        Mockito.verify(financeProcessorClientMock, Mockito.never())
            .getPredictionByTicker(StreamsMetadataFactory.NODE3_URL, "VOO");
    }

    @Test
    void test_getPredictions_with_all_mode_param_and_remote_hosts_return_nothing() {
        setupStateStore(stockPricePredictionStoreMock);
        setupStreamsMetadataForStore(Constants.PREDICTIONS_STORE);

        final var params = new GetPredictionsParams(SearchMode.ALL, null, null, null);
        setupNodeEmptyResponse(StreamsMetadataFactory.NODE2_URL, params);
        setupNodeEmptyResponse(StreamsMetadataFactory.NODE3_URL, params);

        execute_test_getPredictions_with_params_and_remote_hosts_return_nothing(
            params,
            this::setupAllStoreIterator,
            this::verifyAllIteratorCallOnce
        );
    }

    @Test
    void test_getPredictions_with_range_mode_param_and_remote_hosts_return_nothing() {
        final var params = new GetPredictionsParams(SearchMode.RANGE, null, null, null);

        setupStateStore(stockPricePredictionStoreMock);
        final Consumer<List<StockPricePredictionDto>> setupStoreIterator = (list) ->
            Mockito.when(stockPricePredictionStoreMock.range(null, null))
                .thenReturn(createStockPricePredictionInMemoryKeyValueIterator(list));

        @SuppressWarnings("resource") final Runnable verifyRangeCall = () -> Mockito.verify(
            stockPricePredictionStoreMock, Mockito.atMostOnce()
        ).range(null, null);

        execute_test_getPredictions_with_params_and_remote_hosts_return_nothing(
            params,
            setupStoreIterator,
            verifyRangeCall
        );
    }

    @Test
    void test_getPredictions_with_range_mode_param_and_from_A_and_remote_hosts_return_nothing() {
        final var from = "A";
        final var params = new GetPredictionsParams(SearchMode.RANGE, from, null, null);

        setupStateStore(stockPricePredictionStoreMock);
        final Consumer<List<StockPricePredictionDto>> setupStoreIterator = (list) ->
            Mockito.when(stockPricePredictionStoreMock.range(from, null))
                .thenReturn(createStockPricePredictionInMemoryKeyValueIterator(list));

        @SuppressWarnings("resource") final Runnable verifyRangeCall = () -> Mockito.verify(
            stockPricePredictionStoreMock, Mockito.atMostOnce()
        ).range(from, null);

        execute_test_getPredictions_with_params_and_remote_hosts_return_nothing(
            params,
            setupStoreIterator,
            verifyRangeCall
        );
    }

    @Test
    void test_getPredictions_with_reverse_range_mode_param_and_from_V_and_remote_hosts_return_nothing() {
        final var from = "V";
        final var params = new GetPredictionsParams(SearchMode.REVERSE_RANGE, from, null, null);

        setupStateStore(stockPricePredictionStoreMock);
        final Consumer<List<StockPricePredictionDto>> setupStoreIterator = (list) ->
            Mockito.when(stockPricePredictionStoreMock.reverseRange(from, null))
                .thenReturn(createStockPricePredictionInMemoryKeyValueIterator(list));

        @SuppressWarnings("resource") final Runnable verifyRangeCall = () -> Mockito.verify(
            stockPricePredictionStoreMock, Mockito.atMostOnce()
        ).reverseRange(from, null);

        execute_test_getPredictions_with_params_and_remote_hosts_return_nothing(
            params,
            setupStoreIterator,
            verifyRangeCall
        );
    }

    @Test
    void test_getPredictions_with_reverse_all_mode_param_and_remote_hosts_return_nothing() {
        final var params = new GetPredictionsParams(SearchMode.REVERSE_ALL, null, null, null);

        setupStateStore(stockPricePredictionStoreMock);
        final Consumer<List<StockPricePredictionDto>> setupStoreIterator = (list) ->
            Mockito.when(stockPricePredictionStoreMock.reverseAll())
                .thenReturn(createStockPricePredictionInMemoryKeyValueIterator(list));

        @SuppressWarnings("resource") final Runnable verifyRangeCall = () -> Mockito.verify(
            stockPricePredictionStoreMock, Mockito.atMostOnce()
        ).reverseAll();

        execute_test_getPredictions_with_params_and_remote_hosts_return_nothing(
            params,
            setupStoreIterator,
            verifyRangeCall
        );
    }

    @Test
    void test_getPredictions_with_prefixScan_mode_param_and_remote_hosts_return_nothing() {
        final var prefix = "V";
        final var params = new GetPredictionsParams(SearchMode.PREFIX_SCAN, null, null, prefix);

        setupStateStore(stockPricePredictionStoreMock);
        final Consumer<List<StockPricePredictionDto>> setupStoreIterator = (list) ->
            Mockito.when(stockPricePredictionStoreMock.prefixScan(Mockito.eq(prefix), Mockito.any()))
                .thenReturn(createStockPricePredictionInMemoryKeyValueIterator(list));

        final Runnable verifyRangeCall = () -> Mockito.verify(
            stockPricePredictionStoreMock, Mockito.atMostOnce()
        ).prefixScan(Mockito.eq(prefix), Mockito.any());

        execute_test_getPredictions_with_params_and_remote_hosts_return_nothing(
            params,
            setupStoreIterator,
            verifyRangeCall
        );
    }

    @Test
    void test_getPredictionByTicker() {
        // given
        final var ticker = "VOO";

        Mockito.when(kafkaStreamsMock.queryMetadataForKey(
                Mockito.eq(Constants.PREDICTIONS_STORE), Mockito.eq(ticker), Mockito.any(StringSerializer.class)
            ))
            .thenReturn(keyQueryMetadataMock);
        setupStateStore(stockPricePredictionStoreMock);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/predictions/VOO");
        final var inetSocketAddress = mockServerWebExchange.getRequest().getLocalAddress();
        assert inetSocketAddress != null;
        final var hostInfo = new HostInfo(inetSocketAddress.getHostName(), inetSocketAddress.getPort());

        Mockito.when(keyQueryMetadataMock.activeHost()).thenReturn(hostInfo);

        final var vooPair = createVOOSingleItem();
        final var vooPricePredictionDto = vooPair.getLeft();
        final var expectedVOOResponse = vooPair.getRight();

        Mockito.when(stockPricePredictionStoreMock.get(ticker)).thenReturn(vooPricePredictionDto);

        StepVerifier
            // when
            .create(facade.getPredictionByTicker(mockServerWebExchange, ticker))
            // then
            .expectNext(expectedVOOResponse)
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce())
            .queryMetadataForKey(
                Mockito.eq(Constants.PREDICTIONS_STORE), Mockito.eq(ticker), Mockito.any(StringSerializer.class)
            );
        Mockito.verify(keyQueryMetadataMock, Mockito.only()).activeHost();
        verifyClusterNodesNeverCalledForGetPredictionByTicker();
        verifyStoreGotOnce();

        Mockito.verify(stockPricePredictionStoreMock, Mockito.only()).get(ticker);
    }

    @Test
    void test_getPredictionByTicker_and_node_is_not_holder_of_the_ticker_then_get_from_remote_store() {
        // given
        final var ticker = "VOO";

        Mockito.when(kafkaStreamsMock.queryMetadataForKey(
                Mockito.eq(Constants.PREDICTIONS_STORE), Mockito.eq(ticker), Mockito.any(StringSerializer.class)
            ))
            .thenReturn(keyQueryMetadataMock);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/predictions/VOO");
        final var inetSocketAddress = mockServerWebExchange.getRequest().getLocalAddress();
        assert inetSocketAddress != null;
        final var hostInfo = new HostInfo(StreamsMetadataFactory.NODE2_HOSTNAME, StreamsMetadataFactory.SERVER_PORT);

        Mockito.when(keyQueryMetadataMock.activeHost()).thenReturn(hostInfo);

        final var vooPair = createVOOSingleItem();
        final var expectedVOOResponse = vooPair.getRight();
        setup2ndNodeResponseForVOOTicker(Mono.just(expectedVOOResponse));

        StepVerifier
            // when
            .create(facade.getPredictionByTicker(mockServerWebExchange, ticker))
            // then
            .expectNext(expectedVOOResponse)
            .verifyComplete();

        verifyGetKafkaStreamsCalledOnce();
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce())
            .queryMetadataForKey(
                Mockito.eq(Constants.PREDICTIONS_STORE), Mockito.eq(ticker), Mockito.any(StringSerializer.class)
            );
        verifyCluster2ndNodeCalledForTickerVOO();
        verifyStoreNeverObtained();
        Mockito.verify(stockPricePredictionStoreMock, Mockito.never()).get(ticker);
    }

    @Test
    void test_getPredictionByTicker_and_ticker_is_not_found() {
        // given
        final var ticker = "VOO";

        Mockito.when(kafkaStreamsMock.queryMetadataForKey(
                Mockito.eq(Constants.PREDICTIONS_STORE), Mockito.eq(ticker), Mockito.any(StringSerializer.class)
            ))
            .thenReturn(keyQueryMetadataMock);
        setupStateStore(stockPricePredictionStoreMock);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/predictions/VOO");
        final var inetSocketAddress = mockServerWebExchange.getRequest().getLocalAddress();
        assert inetSocketAddress != null;
        final var hostInfo = new HostInfo(inetSocketAddress.getHostName(), inetSocketAddress.getPort());

        Mockito.when(keyQueryMetadataMock.activeHost()).thenReturn(hostInfo);
        Mockito.when(stockPricePredictionStoreMock.get(ticker)).thenReturn(null);

        StepVerifier
            // when
            .create(facade.getPredictionByTicker(mockServerWebExchange, ticker))
            // then
            .expectErrorSatisfies(throwable -> {
                Assertions.assertInstanceOf(EntityNotFoundException.class, throwable);
                final var e = (EntityNotFoundException) throwable;
                Assertions.assertEquals(ErrorCode.TICKER_NOT_FOUND, e.getErrorCode());
            })
            .verify();

        verifyGetKafkaStreamsCalledTwice();
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce())
            .queryMetadataForKey(
                Mockito.eq(Constants.PREDICTIONS_STORE), Mockito.eq(ticker), Mockito.any(StringSerializer.class)
            );
        Mockito.verify(keyQueryMetadataMock, Mockito.only()).activeHost();
        verifyClusterNodesNeverCalledForGetPredictionByTicker();
        verifyStoreGotOnce();

        Mockito.verify(stockPricePredictionStoreMock, Mockito.only()).get(ticker);
    }

    @Test
    void test_getPredictionByTicker_and_remote_host_returns_ticker_is_not_found_error() {
        // given
        final var ticker = "VOO";

        Mockito.when(kafkaStreamsMock.queryMetadataForKey(
                Mockito.eq(Constants.PREDICTIONS_STORE), Mockito.eq(ticker), Mockito.any(StringSerializer.class)
            ))
            .thenReturn(keyQueryMetadataMock);
        Mockito.when(
                financeProcessorClientMock.getPredictionByTicker(
                    StreamsMetadataFactory.NODE2_URL, "VOO"
                )
            )
            .thenReturn(Mono.error(new EntityNotFoundException(ErrorCode.TICKER_NOT_FOUND)));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/predictions/VOO");

        final var hostInfo = new HostInfo(StreamsMetadataFactory.NODE2_HOSTNAME, StreamsMetadataFactory.SERVER_PORT);
        Mockito.when(keyQueryMetadataMock.activeHost()).thenReturn(hostInfo);

        StepVerifier
            // when
            .create(facade.getPredictionByTicker(mockServerWebExchange, ticker))
            // then
            .expectErrorSatisfies(throwable -> {
                Assertions.assertInstanceOf(EntityNotFoundException.class, throwable);
                final var e = (EntityNotFoundException) throwable;
                Assertions.assertEquals(ErrorCode.TICKER_NOT_FOUND, e.getErrorCode());
            })
            .verify();

        verifyGetKafkaStreamsCalledOnce();
        Mockito.verify(kafkaStreamsMock, Mockito.only())
            .queryMetadataForKey(
                Mockito.eq(Constants.PREDICTIONS_STORE), Mockito.eq(ticker), Mockito.any(StringSerializer.class)
            );
        Mockito.verify(keyQueryMetadataMock, Mockito.only()).activeHost();
        verifyCluster2ndNodeCalledForTickerVOO();
        verifyStoreGotOnce();

        Mockito.verify(stockPricePredictionStoreMock, Mockito.never()).get(ticker);
    }

    @Test
    void test_getPredictionTopPredictions() {
        // given
        final var key = Constants.TOP_PREDICTIONS;

        setupStateStore(topPredictionsStoreMock);

        Mockito.when(kafkaStreamsMock.queryMetadataForKey(
                Mockito.eq(Constants.TOP_PREDICTIONS_STORE),
                Mockito.eq(key),
                Mockito.any(StringSerializer.class)
            ))
            .thenReturn(keyQueryMetadataMock);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/top/predictions");
        final var inetSocketAddress = mockServerWebExchange.getRequest().getLocalAddress();
        assert inetSocketAddress != null;
        final var hostInfo = new HostInfo(inetSocketAddress.getHostName(), inetSocketAddress.getPort());

        Mockito.when(keyQueryMetadataMock.activeHost()).thenReturn(hostInfo);

        final var topPredictionSet = createTopPredictions();
        Mockito.when(topPredictionsStoreMock.get(key))
            .thenReturn(topPredictionSet);

        StepVerifier
            // when
            .create(facade.getTopPredictions(mockServerWebExchange))
            // then
            .expectNextSequence(topPredictionSet.reversed())
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce())
            .queryMetadataForKey(
                Mockito.eq(Constants.TOP_PREDICTIONS_STORE),
                Mockito.eq(key),
                Mockito.any(StringSerializer.class)
            );
        Mockito.verify(keyQueryMetadataMock, Mockito.only()).activeHost();
        verifyClusterNodesNeverCalledForGetTopPredictions();
        verifyStoreGotOnce();

        Mockito.verify(topPredictionsStoreMock, Mockito.only()).get(key);
    }

    private SortedSet<TopPredictionResponse> createTopPredictions() {
        final var vooPair = createVOOSingleItem();
        final var vooPredictionResponse = TopPredictionFactory.of(vooPair.getLeft());

        final var spyPair = createSPYSingleItem();
        final var spyPredictionResponse = TopPredictionFactory.of(spyPair.getLeft());

        final var googlPair = createGOOGLSingleItem();
        final var googlPredictionResponse = TopPredictionFactory.of(googlPair.getLeft());

        final var vtsaxPair = createVTSAXSingleItem();
        final var vtsaxPredictionResponse = TopPredictionFactory.of(vtsaxPair.getLeft());

        return new TreeSet<>(Set.of(
            vooPredictionResponse,
            spyPredictionResponse,
            googlPredictionResponse,
            vtsaxPredictionResponse
        ));
    }

    @Test
    void test_getPredictionTopPredictions_and_the_value_is_null() {
        // given
        final var key = Constants.TOP_PREDICTIONS;

        setupStateStore(topPredictionsStoreMock);

        Mockito.when(kafkaStreamsMock.queryMetadataForKey(
                Mockito.eq(Constants.TOP_PREDICTIONS_STORE),
                Mockito.eq(key),
                Mockito.any(StringSerializer.class)
            ))
            .thenReturn(keyQueryMetadataMock);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/top/predictions");
        final var inetSocketAddress = mockServerWebExchange.getRequest().getLocalAddress();
        assert inetSocketAddress != null;
        final var hostInfo = new HostInfo(inetSocketAddress.getHostName(), inetSocketAddress.getPort());

        Mockito.when(keyQueryMetadataMock.activeHost()).thenReturn(hostInfo);

        Mockito.when(topPredictionsStoreMock.get(key)).thenReturn(null);

        StepVerifier
            // when
            .create(facade.getTopPredictions(mockServerWebExchange))
            // then
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce())
            .queryMetadataForKey(
                Mockito.eq(Constants.TOP_PREDICTIONS_STORE),
                Mockito.eq(key),
                Mockito.any(StringSerializer.class)
            );
        Mockito.verify(keyQueryMetadataMock, Mockito.only()).activeHost();
        verifyClusterNodesNeverCalledForGetTopPredictions();
        verifyStoreGotOnce();

        Mockito.verify(topPredictionsStoreMock, Mockito.only()).get(key);
    }

    @Test
    void test_getTopPredictions_and_ticker_is_not_present_in_local_store_then_get_from_remote_store() {
        // given
        final var key = Constants.TOP_PREDICTIONS;

        final var topPredictionSet = createTopPredictions();
        final var responseFlux = Flux.fromIterable(topPredictionSet.reversed());
        setupNodeTopPredictionResponse(StreamsMetadataFactory.NODE2_URL, responseFlux);

        Mockito.when(kafkaStreamsMock.queryMetadataForKey(
                Mockito.eq(Constants.TOP_PREDICTIONS_STORE),
                Mockito.eq(key),
                Mockito.any(StringSerializer.class)
            ))
            .thenReturn(keyQueryMetadataMock);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/top/predictions");
        final var inetSocketAddress = mockServerWebExchange.getRequest().getLocalAddress();
        assert inetSocketAddress != null;
        final var hostInfo = new HostInfo(StreamsMetadataFactory.NODE2_HOSTNAME, StreamsMetadataFactory.SERVER_PORT);

        Mockito.when(keyQueryMetadataMock.activeHost()).thenReturn(hostInfo);

        StepVerifier
            // when
            .create(facade.getTopPredictions(mockServerWebExchange))
            // then
            .expectNextSequence(topPredictionSet.reversed())
            .verifyComplete();

        verifyGetKafkaStreamsCalledOnce();
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce())
            .queryMetadataForKey(
                Mockito.eq(Constants.TOP_PREDICTIONS_STORE),
                Mockito.eq(key),
                Mockito.any(StringSerializer.class)
            );
        Mockito.verify(keyQueryMetadataMock, Mockito.only()).activeHost();
        verifyCluster2ndNodeCalledForGetTopPredictions();
        verifyStoreNeverObtained();

        Mockito.verify(topPredictionsStoreMock, Mockito.never()).get(key);
    }

    private void verifyStreamsMetadataForLoss20PercentStoreFetchedOnce() {
        Mockito.verify(kafkaStreamsMock, Mockito.atMostOnce())
            .streamsMetadataForStore(Constants.LOSS_PREDICTIONS_STORE);
    }

    @Test
    void test_getLossPredictions_and_remote_hosts_return_nothing() {
        // given
        setupStateStore(lossPredictionsStoreMock);

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/loss/predictions");

        final var voo = new KeyValue<>("VOO", "341.3360107421874");
        final var keyValues = List.of(
            voo
        );
        Mockito.when(lossPredictionsStoreMock.all())
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var lossPredictionResponse = new LossPredictionResponse(voo.key, new BigDecimal(voo.value));

        StepVerifier
            // when
            .create(facade.getLossPredictions(mockServerWebExchange))
            // then
            .expectNext(lossPredictionResponse)
            .verifyComplete();

        verifyGetKafkaStreamsCalledTwice();
        verifyStreamsMetadataForLoss20PercentStoreFetchedOnce();
        verifyClusterNodesCalledOnce();
        verifyStoreGotOnce();
        Mockito.verify(lossPredictionsStoreMock, Mockito.only()).all();
    }

    @Test
    void test_getLossPredictions_and_remote_hosts_return_data() {
        // given
        setupStreamsMetadataForStore(Constants.LOSS_PREDICTIONS_STORE);
        setupStateStore(lossPredictionsStoreMock);

        final var googlLossPredictionResponse = new LossPredictionResponse(
            "GOOGL", BigDecimal.valueOf(132.52000427246094)
        );
        setupNodeLossPredictionResponse(StreamsMetadataFactory.NODE2_URL, Flux.just(googlLossPredictionResponse));

        final var spyLossPredictionResponse = new LossPredictionResponse(
            "SPY", BigDecimal.valueOf(464.1000061035156)
        );
        setupNodeLossPredictionResponse(StreamsMetadataFactory.NODE3_URL, Flux.just(spyLossPredictionResponse));

        final var mockServerWebExchange = ServerWebExchangeFactory.
            createGetServerRequest("/api/v1/loss/predictions");

        final var voo = new KeyValue<>("VOO", "341.3360107421874");
        final var keyValues = List.of(
            voo
        );
        Mockito.when(lossPredictionsStoreMock.all())
            .thenReturn(new InMemoryKeyValueIterator<>(keyValues));

        final var vooLossPredictionResponse = new LossPredictionResponse(voo.key, new BigDecimal(voo.value));

        final var responses = new HashSet<LossPredictionResponse>();
        StepVerifier
            // when
            .create(facade.getLossPredictions(mockServerWebExchange))
            // then
            .consumeNextWith(responses::add)
            .consumeNextWith(responses::add)
            .consumeNextWith(responses::add)
            .verifyComplete();

        final var expectedLossPredictionResponses = List.of(
            vooLossPredictionResponse,
            spyLossPredictionResponse,
            googlLossPredictionResponse
        );
        Assertions.assertTrue(responses.containsAll(expectedLossPredictionResponses));

        verifyGetKafkaStreamsCalledTwice();
        verifyStreamsMetadataForLoss20PercentStoreFetchedOnce();
        verifyClusterNodesCalledOnce();
        verifyStoreGotOnce();
        Mockito.verify(lossPredictionsStoreMock, Mockito.only()).all();
    }
}
