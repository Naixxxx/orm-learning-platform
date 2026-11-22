FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -Dspotless.skip=true dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S learning && adduser -S learning -G learning
WORKDIR /app
COPY --from=build --chown=learning:learning /workspace/target/orm-learning-platform-1.0.0.jar app.jar
USER learning
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
