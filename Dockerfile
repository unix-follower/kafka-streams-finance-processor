FROM openjdk:21-slim

WORKDIR /opt

ARG APP_VERSION=1.0

RUN jar_file=$(ls | grep -v '.*-plain.jar')
COPY $jar_file ./app.jar

EXPOSE 8080
EXPOSE 5005
ENTRYPOINT [\
"java", \
"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", \
"-XX:+AllowRedefinitionToAddDeleteMethods", \
"-jar", "/opt/app.jar"\
]
