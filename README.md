### Docker commands
#### Build an image
```shell
docker build --build-arg APP_VERSION=2024.04.0 -t finance-processor:latest .
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
curl -v "${fin_processor_url}/api/v1/prices?mode=fetch&key=VOO&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=fetchKeyRange&keyFrom=A&keyTo=C&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=fetchAll&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=backwardFetch&key=VOO&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=backwardFetchAll&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=backwardFetchKeyRange&keyFrom=A&keyTo=C&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
```
##### Get session-based windows
```shell
curl -v "${fin_processor_url}/api/v1/prices?mode=sFetch&key=AAPL" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sFetch&key=AAL" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sessionWindowFetchKeyRange&keyFrom=A&keyTo=C" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sBackwardFetch&key=AAPL" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sFetchSession&key=AAPL&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sFindSessions&key=AAPL&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sFindSessions&key=AAPL&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sBackwardFetchKeyRange&keyFrom=A&keyTo=C" | jq
curl -v "${fin_processor_url}/api/v1/prices?mode=sBackwardFindSessions&key=AAPL&timeFrom=2024-03-26T19:04:40.859Z&timeTo=2024-03-26T19:05:11.146Z" | jq
```
### Reset Kafka streams application
```shell
${kafka_dir}/kafka-streams-application-reset.sh --bootstrap-server $bootstrap_server \
 --application-id fin-processor
```
