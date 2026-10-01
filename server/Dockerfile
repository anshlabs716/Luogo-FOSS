FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
RUN apk add --no-cache kotlin
COPY RelayServer.kt .
RUN kotlinc RelayServer.kt -include-runtime -d luogo-relay.jar

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /src/luogo-relay.jar /usr/local/bin/luogo-relay.jar
EXPOSE 8040
ENTRYPOINT ["java", "-jar", "/usr/local/bin/luogo-relay.jar"]
