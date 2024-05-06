package org.example.finprocessor.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBeanBuilder;
import io.netty.channel.ChannelOption;
import org.example.finprocessor.stockmarket.api.StockType;
import org.example.finprocessor.stockmarket.api.StockTypeCsvRecord;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.config.DelegatingWebFluxConfiguration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookServerHandler;
import org.zalando.logbook.spring.webflux.LogbookExchangeFilterFunction;
import reactor.netty.channel.MicrometerChannelMetricsRecorder;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class AppConfig extends DelegatingWebFluxConfiguration {
    public static final Duration DEFAULT_PUBLISHER_TIMEOUT = Duration.ofSeconds(60);

    private final AppProperties appProperties;

    public AppConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    protected void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        super.configureHttpMessageCodecs(configurer);

        final var mapper = objectMapper();

        final var defaultCodecs = configurer.defaultCodecs();
        final var mediaTypes = new MimeType[]{
            MediaType.APPLICATION_JSON,
            MediaType.ALL
        };
        defaultCodecs.jackson2JsonEncoder(new Jackson2JsonEncoder(mapper, mediaTypes));
        defaultCodecs.jackson2JsonDecoder(new Jackson2JsonDecoder(mapper, mediaTypes));
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, true);
    }

    @Bean
    NettyServerCustomizer nettyServerCustomizer(Logbook logbook) {
        return server -> server
            .metrics(true, () -> new MicrometerChannelMetricsRecorder("netty_server", "http"))
            .doOnConnection(connection -> connection.addHandlerLast(new LogbookServerHandler(logbook)));
    }

    @Bean
    WebClientCustomizer finProcessorWebClientCustomizer(ObjectMapper objectMapper, Logbook logbook) {
        final var finProcessor = appProperties.finProcessor();

        final var httpClient = HttpClient.create(
                ConnectionProvider.builder("finProcessor")
                    .maxIdleTime(finProcessor.connectionMaxIdleTime())
                    .maxLifeTime(finProcessor.connectionMaxLifeTime())
                    .evictInBackground(finProcessor.connectionEvictionTime())
                    .build()
            )
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) finProcessor.connectionTimeout().toMillis())
            .responseTimeout(finProcessor.readTimeout());

        return webClientBuilder -> webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient))
            .filter(new LogbookExchangeFilterFunction(logbook))
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> {
                    final var customCodecs = configurer.customCodecs();
                    customCodecs.register(new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                    customCodecs.register(new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
                })
                .build());
    }

    @Bean
    WebClient financeProcessorWebClient(WebClientCustomizer finProcessorWebClientCustomizer) {
        final var builder = WebClient.builder();
        finProcessorWebClientCustomizer.customize(builder);
        return builder.build();
    }

    @Bean
    public Map<StockType, Set<String>> stockTypeMap() {
        final var csvInStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("stock-type-mapping.csv");
        Objects.requireNonNull(csvInStream);

        try (final var csvReader = new CSVReader(new InputStreamReader(csvInStream))) {
            final var csvToBean = new CsvToBeanBuilder<StockTypeCsvRecord>(csvReader)
                .withType(StockTypeCsvRecord.class)
                .build();

            final var stockTypes = csvToBean.stream().collect(
                Collectors.toMap(StockTypeCsvRecord::getTicker, StockTypeCsvRecord::getType)
            );

            return stockTypes.entrySet().stream()
                .collect(Collectors.groupingBy(
                    Map.Entry::getValue,
                    Collectors.mapping(Map.Entry::getKey, Collectors.toSet())
                ));
        } catch (IOException e) {
            throw new BeanCreationException(e.getMessage(), e);
        }
    }
}
