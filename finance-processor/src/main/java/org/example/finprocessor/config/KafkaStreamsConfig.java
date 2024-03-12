package org.example.finprocessor.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.example.finprocessor.exception.FinProcessorProductionExceptionHandler;
import org.example.finprocessor.exception.FinProcessorStreamsUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.streams.RecoveringDeserializationExceptionHandler;

import java.time.Duration;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {
    private final KafkaProperties kafkaProperties;

    private final AppProperties appProperties;

    public KafkaStreamsConfig(KafkaProperties kafkaProperties, AppProperties appProperties) {
        this.kafkaProperties = kafkaProperties;
        this.appProperties = appProperties;
    }

    @SuppressWarnings("resource")
    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfig() {
        final var props = kafkaProperties.buildStreamsProperties(null);

        final var consumer = kafkaProperties.getConsumer();
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.getMaxPollRecords());

        final var maxPollIntervalMsAsString = consumer.getProperties()
            .get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG);
        final int maxPollIntervalMs = (int) Duration.parse(maxPollIntervalMsAsString).toMillis();
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);

        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, RecoveringDeserializationExceptionHandler.class);
        props.put(RecoveringDeserializationExceptionHandler.KSTREAM_DESERIALIZATION_RECOVERER, dltRecoverer());
        props.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG, FinProcessorProductionExceptionHandler.class);

        final var stateDir = props.get(StreamsConfig.STATE_DIR_CONFIG);
        if (stateDir == null || stateDir.equals("")) {
            props.put(StreamsConfig.STATE_DIR_CONFIG, System.getProperty("java.io.tmpdir"));
        }

        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    KafkaAdmin kafkaAdmin() {
        final var configs = kafkaProperties.getAdmin().buildProperties(null);
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        return new KafkaAdmin(configs);
    }

    @Bean
    NewTopic stockMarketTopic() {
        final var topic = appProperties.stockMarketRouterStream().inputTopic();
        return TopicBuilder.name(topic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    private NewTopic createNewRouterTopic(String topicSuffix) {
        final var topicPrefix = appProperties.stockMarketRouterStream().inputTopic();
        final var topic = topicPrefix + topicSuffix;
        return TopicBuilder.name(topic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    NewTopic etfTopic() {
        final var topicSuffix = appProperties.stockMarketRouterStream().etfTopicSuffix();
        return createNewRouterTopic(topicSuffix);
    }

    @Bean
    NewTopic fundTopic() {
        final var topicSuffix = appProperties.stockMarketRouterStream().fundTopicSuffix();
        return createNewRouterTopic(topicSuffix);
    }

    @Bean
    NewTopic defaultTopic() {
        final var topicSuffix = appProperties.stockMarketRouterStream().shareTopicSuffix();
        return createNewRouterTopic(topicSuffix);
    }

    @Bean
    NewTopic predictionsTopic() {
        final var topic = appProperties.stockMarketStream().outputTopic();
        return TopicBuilder.name(topic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    StreamsBuilderFactoryBeanConfigurer streamsBuilderConfigurer() {
        return bean -> bean.setStreamsUncaughtExceptionHandler(new FinProcessorStreamsUncaughtExceptionHandler());
    }

    @Bean
    KafkaTemplate<String, byte[]> dltKafkaTemplate() {
        final var props = kafkaProperties.buildProducerProperties(null);

        final var producerFactory = new DefaultKafkaProducerFactory<>(
            props, new StringSerializer(), new ByteArraySerializer()
        );

        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    DeadLetterPublishingRecoverer dltRecoverer() {
        return new DeadLetterPublishingRecoverer(dltKafkaTemplate());
    }
}
