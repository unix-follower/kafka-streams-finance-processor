package org.example.finprocessor.test;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.StreamsMetadata;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.internals.StreamsMetadataImpl;
import org.example.finprocessor.util.Constants;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class StreamsMetadataFactory {
    private static final byte FIRST_PARTITION_INDEX = 0;
    private static final byte SECOND_PARTITION_INDEX = 1;
    private static final byte THIRD_PARTITION_INDEX = 2;

    public static final short SERVER_PORT = 8080;
    public static final String NODE2_HOSTNAME = "fin-processor-node2";
    public static final String REMOTE_HOST_URL_FORMAT = "http://%s:%d";
    public static final String NODE2_URL = String.format(REMOTE_HOST_URL_FORMAT, NODE2_HOSTNAME, SERVER_PORT);
    public static final String NODE3_HOSTNAME = "fin-processor-node3";
    public static final String NODE3_URL = String.format(REMOTE_HOST_URL_FORMAT, NODE3_HOSTNAME, SERVER_PORT);

    public static Collection<StreamsMetadata> clusterOf3Nodes() {
        final var stores = Set.of(
            Constants.PREDICTIONS_STORE,
            Constants.TOP_PREDICTIONS_STORE,
            Constants.LOSS_PREDICTIONS_STORE
        );

        final var stockMarketTopic = "stock-market";
        final var stockEtfMarketTopic = "stock-market-etf";
        final var stockFundMarketTopic = "stock-market-fund";
        final var stockShareMarketTopic = "stock-market-share";
        final var predictionsTopic = "predictions";

        final var node1TopicPartitions = Set.of(
            new TopicPartition(stockMarketTopic, FIRST_PARTITION_INDEX),
            new TopicPartition(stockEtfMarketTopic, FIRST_PARTITION_INDEX),
            new TopicPartition(stockFundMarketTopic, FIRST_PARTITION_INDEX),
            new TopicPartition(stockShareMarketTopic, FIRST_PARTITION_INDEX),
            new TopicPartition(predictionsTopic, FIRST_PARTITION_INDEX)
        );

        final var localhost = new StreamsMetadataImpl(
            new HostInfo("localhost", SERVER_PORT),
            stores,
            node1TopicPartitions,
            Collections.emptySet(),
            Collections.emptySet()
        );

        final var node2TopicPartitions = Set.of(
            new TopicPartition(stockMarketTopic, SECOND_PARTITION_INDEX),
            new TopicPartition(stockEtfMarketTopic, SECOND_PARTITION_INDEX),
            new TopicPartition(stockFundMarketTopic, SECOND_PARTITION_INDEX),
            new TopicPartition(stockShareMarketTopic, SECOND_PARTITION_INDEX),
            new TopicPartition(predictionsTopic, SECOND_PARTITION_INDEX)
        );

        final var node2 = new StreamsMetadataImpl(
            new HostInfo(NODE2_HOSTNAME, SERVER_PORT),
            stores,
            node2TopicPartitions,
            Collections.emptySet(),
            Collections.emptySet()
        );

        final var node3TopicPartitions = Set.of(
            new TopicPartition(stockMarketTopic, THIRD_PARTITION_INDEX),
            new TopicPartition(stockEtfMarketTopic, THIRD_PARTITION_INDEX),
            new TopicPartition(stockFundMarketTopic, THIRD_PARTITION_INDEX),
            new TopicPartition(stockShareMarketTopic, THIRD_PARTITION_INDEX),
            new TopicPartition(predictionsTopic, THIRD_PARTITION_INDEX)
        );

        final var node3 = new StreamsMetadataImpl(
            new HostInfo(NODE3_HOSTNAME, SERVER_PORT),
            stores,
            node3TopicPartitions,
            Collections.emptySet(),
            Collections.emptySet()
        );

        return Set.of(localhost, node2, node3);
    }
}
