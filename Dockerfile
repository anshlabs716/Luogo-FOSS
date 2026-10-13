# Backend image for Luogo-FOSS. Adjust to the actual build tooling.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY relay/ ./relay/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "relay/backend.jar"]
