FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy root pom and module poms for layer caching
COPY pom.xml .
COPY monitoring-core/pom.xml monitoring-core/pom.xml
COPY monitoring-spring-boot-starter/pom.xml monitoring-spring-boot-starter/pom.xml
COPY monitoring-server/pom.xml monitoring-server/pom.xml

# Download dependencies before copying source (cache layer)
RUN mvn dependency:go-offline -pl monitoring-core,monitoring-spring-boot-starter,monitoring-server -am -q 2>/dev/null || true

# Copy source
COPY monitoring-core/src monitoring-core/src
COPY monitoring-spring-boot-starter/src monitoring-spring-boot-starter/src
COPY monitoring-server/src monitoring-server/src

RUN mvn clean package -pl monitoring-core,monitoring-spring-boot-starter,monitoring-server -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY --from=builder /build/monitoring-server/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
