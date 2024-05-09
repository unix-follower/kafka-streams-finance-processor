package org.example.finprocessor.config;

import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.example.finprocessor.component.TracingConsumerInterceptor;
import org.example.finprocessor.component.TracingProducerInterceptor;
import org.example.finprocessor.exception.FinProcessorProductionExceptionHandler;
import org.example.finprocessor.exception.FinProcessorStreamsUncaughtExceptionHandler;
import org.springframework.beans.factory.BeanCreationException;
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {
    private static final byte APP_SERVER_HOST_REGEX_GROUP_INDEX = 1;
    private static final byte APP_SERVER_PORT_REGEX_GROUP_INDEX = 2;

    private final KafkaProperties kafkaProperties;

    private final AppProperties appProperties;

    public KafkaStreamsConfig(KafkaProperties kafkaProperties, AppProperties appProperties) {
        this.kafkaProperties = kafkaProperties;
        this.appProperties = appProperties;
    }

    @SuppressWarnings("resource")
    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfig(ObservationRegistry observationRegistry) {
        final var props = kafkaProperties.buildStreamsProperties(null);

        final var consumer = kafkaProperties.getConsumer();
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.getMaxPollRecords());

        final var maxPollIntervalMsAsString = consumer.getProperties()
            .get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG);
        final int maxPollIntervalMs = (int) Duration.parse(maxPollIntervalMsAsString).toMillis();
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);

        props.put(
            StreamsConfig.PRODUCER_PREFIX + ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
            TracingProducerInterceptor.class.getName()
        );
        props.put(
            StreamsConfig.CONSUMER_PREFIX + ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
            TracingConsumerInterceptor.class.getName()
        );
        props.put(ObservationRegistry.class.getName(), observationRegistry);

        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, RecoveringDeserializationExceptionHandler.class);
        props.put(RecoveringDeserializationExceptionHandler.KSTREAM_DESERIALIZATION_RECOVERER, dltRecoverer());
        props.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG, FinProcessorProductionExceptionHandler.class);

        final var stateDir = props.get(StreamsConfig.STATE_DIR_CONFIG);
        if (stateDir == null || stateDir.equals("")) {
            props.put(StreamsConfig.STATE_DIR_CONFIG, System.getProperty("java.io.tmpdir"));
        }

        configureApplicationServerProp(props);

        return new KafkaStreamsConfiguration(props);
    }

    private static void configureApplicationServerProp(Map<String, Object> props) {
        final var appServer = (String) props.get(StreamsConfig.APPLICATION_SERVER_CONFIG);
        final var appServerPattern = Pattern.compile("^\\$\\((.*):(\\d{2,4})\\)$");
        final var matcher = appServerPattern.matcher(appServer);
        if (matcher.matches()) {
            final var host = matcher.group(APP_SERVER_HOST_REGEX_GROUP_INDEX);
            if (!"localhostIPv4".equalsIgnoreCase(host)) {
                throw new UnsupportedOperationException(String.format("Unsupported operand: %s", host));
            }

            final int port = Integer.parseInt(matcher.group(APP_SERVER_PORT_REGEX_GROUP_INDEX));
            final InetAddress localHostIpv4;
            try {
                localHostIpv4 = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                throw new BeanCreationException(e.getMessage(), e);
            }
            final var ip4hostAddress = localHostIpv4.getHostAddress();
            props.put(StreamsConfig.APPLICATION_SERVER_CONFIG, String.format("%s:%d", ip4hostAddress, port));
        }
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

    @Bean
    NewTopic stockMarketDltTopic() {
        final var topic = appProperties.stockMarketRouterStream().inputTopic();
        return TopicBuilder.name(String.format("%s.DLT", topic))
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

        final var template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    DeadLetterPublishingRecoverer dltRecoverer() {
        return new DeadLetterPublishingRecoverer(dltKafkaTemplate());
    }
}
