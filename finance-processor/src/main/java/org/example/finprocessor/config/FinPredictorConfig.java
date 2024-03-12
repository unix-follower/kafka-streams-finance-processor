package org.example.finprocessor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.webflux.LogbookExchangeFilterFunction;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
class FinPredictorConfig {
    private final AppProperties appProperties;

    FinPredictorConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    WebClientCustomizer finPredictorWebClientCustomizer(ObjectMapper objectMapper, Logbook logbook) {
        final var finPredictor = appProperties.finPredictor();

        final var httpClient = HttpClient.create(
                ConnectionProvider.builder("finPredictor")
                    .maxIdleTime(finPredictor.connectionMaxIdleTime())
                    .maxLifeTime(finPredictor.connectionMaxLifeTime())
                    .evictInBackground(finPredictor.connectionEvictionTime())
                    .build()
            )
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) finPredictor.connectionTimeout().toMillis())
            .responseTimeout(finPredictor.readTimeout());

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
    WebClient financePredictorWebClient(WebClientCustomizer finPredictorWebClientCustomizer) {
        final var finPredictor = appProperties.finPredictor();
        final var url = finPredictor.url().toString();

        final var builder = WebClient.builder()
            .baseUrl(url);

        finPredictorWebClientCustomizer.customize(builder);

        return builder.build();
    }
}
