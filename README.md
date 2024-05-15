### Docker commands
#### Build an image
```shell
docker build --build-arg APP_VERSION=2024.05.0 -t finance-processor:latest .
```
#### Start Kafka in Docker container
```shell
docker-compose -f docker/kafka/docker-compose.yml up -d
```
#### Stop Kafka Docker container
```shell
docker-compose -f docker/kafka/docker-compose.yml stop
```
#### Destroy Kafka Docker container
```shell
docker-compose -f docker/kafka/docker-compose.yml down -v
```
#### Create network
```shell
docker network create finance-predictor-net
```
#### Run finance-processor-node-1
```shell
docker run \
  --rm \
  --name finance-processor-node-1 \
  --hostname finance-processor-node-1 \
  --network finance-predictor-net \
  --publish 8080:8080 \
  --publish 5005:5005 \
  -e SERVER_PORT=8080 \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=192.168.105.6:9094 \
  -e SPRING_KAFKA_STREAMS_PROPERTIES_APPLICATION_SERVER=http://finance-processor-node-1:8080 \
  -e APP_FIN_PREDICTOR_URL=http://finance-predictor:5000 \
  -e APP_STOCK_MARKET_STREAM_WINDOW=PT30s \
  finance-processor:latest
```
#### Run finance-processor-node-2
```shell
docker run \
  --rm \
  --name finance-processor-node-2 \
  --hostname finance-processor-node-2 \
  --network finance-predictor-net \
  --publish 8081:8080 \
  --publish 5006:5005 \
  -e SERVER_PORT=8080 \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=192.168.105.6:9094 \
  -e SPRING_KAFKA_STREAMS_PROPERTIES_APPLICATION_SERVER=http://finance-processor-node-2:8080 \
  -e APP_FIN_PREDICTOR_URL=http://finance-predictor:5000 \
  -e APP_STOCK_MARKET_STREAM_WINDOW=PT30s \
  finance-processor:latest
```
#### Connect to running container 
```shell
docker exec -it finance-processor-node-1 bash
```
### Define variables
```shell
kafka_dir=/opt/kafka_2.13-3.5.0/bin
bootstrap_server="$(minikube ip):9094"
fin_processor_url="$(minikube ip):8080"
topic=stock-market
#topic=stock-market.DLT
#topic=stock-market-etf
#topic=stock-market-share
#topic=stock-market-fund
#topic=fin-processor-top-predictions-STATE-STORE-changelog
#topic=fin-processor-loss-predictions-STATE-STORE-changelog
```
## Kafka shell commands
### Topics
#### List
```shell
${kafka_dir}/kafka-topics.sh --bootstrap-server $bootstrap_server --list
```
#### Describe
```shell
${kafka_dir}/kafka-topics.sh --bootstrap-server $bootstrap_server \
  --topic $topic \
  --describe
```
#### Describe __transaction_state
```shell
${kafka_dir}/kafka-topics.sh --bootstrap-server $bootstrap_server --topic __transaction_state --describe
```
#### Delete
```shell
${kafka_dir}/kafka-topics.sh --bootstrap-server $bootstrap_server \
  --topic stock-market \
  --delete
```
### Consumer groups
#### List
```shell
${kafka_dir}/kafka-consumer-groups.sh --bootstrap-server $bootstrap_server --list
```
#### Describe
```shell
${kafka_dir}/kafka-consumer-groups.sh --bootstrap-server $bootstrap_server --describe \
  --all-groups \
  --verbose
```
```shell
${kafka_dir}/kafka-consumer-groups.sh --bootstrap-server $bootstrap_server --describe \
  --group fin-processor \
  --verbose
```
### Offsets
#### Describe
```shell
${kafka_dir}/kafka-get-offsets.sh --bootstrap-server $bootstrap_server
```
### Produce data
```shell
${kafka_dir}/kafka-console-producer.sh --bootstrap-server $bootstrap_server --topic stock-market < tmp.json
```
### Consume data
```shell
${kafka_dir}/kafka-console-consumer.sh --bootstrap-server $bootstrap_server \
 --topic $topic \
 --from-beginning \
 --partition 0
```
## API calls
#### Get Prometheus metrics
```shell
curl -v ${fin_processor_url}/actuator/prometheus
```
#### Get predictions
```shell
curl -v ${fin_processor_url}/api/v1/predictions/EPAM | jq
curl -v localhost:8082/api/v1/predictions/EPAM | jq
curl -v ${fin_processor_url}/api/v1/predictions/EPA?mode=prefixScan | jq
curl -v ${fin_processor_url}/api/v1/local/predictions | jq
curl -v ${fin_processor_url}/api/v1/predictions | jq
curl -v "${fin_processor_url}/api/v1/predictions?mode=range&from=A&to=C" | jq
curl -v "${fin_processor_url}/api/v1/predictions?mode=range&from=E" | jq
curl -v "${fin_processor_url}/api/v1/predictions?mode=range&to=E" | jq
curl -v "${fin_processor_url}/api/v1/predictions?mode=reverseRange&from=C&to=A" | jq
curl -v "${fin_processor_url}/api/v1/predictions?mode=reverseRange&from=E" | jq
curl -v "${fin_processor_url}/api/v1/predictions?mode=reverseRange&to=E" | jq
curl -v "${fin_processor_url}/api/v1/predictions?mode=reverseAll" | jq
curl -v ${fin_processor_url}/api/v1/top/predictions | jq
curl -v ${fin_processor_url}/api/v1/local/top/predictions | jq
curl -v ${fin_processor_url}/api/v1/loss/predictions | jq
curl -v ${fin_processor_url}/api/v1/local/loss/predictions | jq
```
#### Get prices
##### Get time-based windows
```shell
curl -v "${fin_processor_url}/api/v1/prices?mode=all" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=backwardAll" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=fetch&key=VOO&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=fetchKeyRange&keyFrom=A&keyTo=C&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=fetchAll&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=backwardFetch&key=VOO&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=backwardFetchAll&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=backwardFetchKeyRange&keyFrom=A&keyTo=C&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
```
##### Get session-based windows
```shell
curl -v "${fin_processor_url}/api/v1/prices?mode=sFetch&key=AAPL" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sBackwardFetch&key=AAPL" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sFetchSession&key=AAPL&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sFindSessions&key=HPE&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sFindSessions&key=AAPL&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sBackwardFetchKeyRange&keyFrom=A&keyTo=B" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sBackwardFindSessions&key=AAPL&timeFrom=2024-04-27T15:00:00.000Z&timeTo=2024-04-27T16:00:00.000Z" | jq
```
### Reset Kafka Streams application
```shell
${kafka_dir}/kafka-streams-application-reset.sh --bootstrap-server $bootstrap_server \
 --application-id fin-processor
```
### Query the metrics
#### JVM metrics
```promql
rate(jvm_threads_started_threads_total[30m])
avg_over_time(jvm_threads_started_threads_total[30m])
jvm_threads_states_threads
```
#### HTTP server request metrics
```promql
max_over_time(http_server_requests_active_seconds_max[30m])
quantile(0.9, http_server_requests_active_seconds_duration_sum)
sum_over_time(http_server_requests_active_seconds_duration_sum[30m])
```
#### Kafka Streams task metrics
```promql
irate(kafka_stream_task_dropped_records_total[30m])
irate(kafka_stream_task_restore_rate[30m])
max_over_time(kafka_stream_task_active_buffer_count[30m])
```
#### Kafka Streams thread metrics
```promql
max_over_time(kafka_stream_thread_process_records_max[30m])
delta(kafka_stream_thread_poll_rate[30m])
max_over_time(kafka_stream_thread_poll_records_max[30m])
increase(kafka_stream_thread_process_total[30m])
```
#### Kafka Streams topic metrics
```promql
increase(kafka_stream_topic_records_produced_total[30m])
increase(kafka_stream_topic_bytes_consumed_total[30m])
```
#### Kafka producer metrics
```promql
deriv(kafka_producer_metadata_age[30m])
increase(kafka_producer_node_outgoing_byte_total[30m])
```
#### Kafka consumer metrics
```promql
increase(kafka_consumer_fetch_manager_records_consumed_total[30m])
max_over_time(kafka_consumer_request_size_max[30m])
avg_over_time(kafka_consumer_request_size_max[30m])
increase(kafka_consumer_incoming_byte_total[30m])
max(kafka_consumer_incoming_byte_rate)
```
#### Kafka Streams consumer coordinator metrics
```promql
increase(kafka_consumer_coordinator_rebalance_total[30m])
```
#### Kafka Streams state store metrics
```promql
max_over_time(kafka_stream_state_num_entries_active_mem_table[30m])
max_over_time(kafka_stream_state_num_running_compactions[30m])
increase(kafka_stream_state_bytes_read_total[1d])
max_over_time(kafka_stream_state_bytes_read_rate[30m])
```
