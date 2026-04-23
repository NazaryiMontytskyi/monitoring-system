ARG SERVICE_MODULE

FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy root pom and monitoring module poms for layer caching
COPY pom.xml .
COPY monitoring-core/pom.xml monitoring-core/pom.xml
COPY monitoring-spring-boot-starter/pom.xml monitoring-spring-boot-starter/pom.xml

# Copy demo poms for layer caching
COPY demo/pom.xml demo/pom.xml
COPY demo/demo-inventory-service/pom.xml demo/demo-inventory-service/pom.xml
COPY demo/demo-order-service/pom.xml demo/demo-order-service/pom.xml
COPY demo/demo-payment-service/pom.xml demo/demo-payment-service/pom.xml

# Download dependencies before copying source (cache layer)
RUN mvn dependency:go-offline -pl monitoring-core,monitoring-spring-boot-starter -am -q 2>/dev/null || true

# Install monitoring-core and starter into local Maven repo so demo services can resolve them
COPY monitoring-core/src monitoring-core/src
COPY monitoring-spring-boot-starter/src monitoring-spring-boot-starter/src

RUN mvn install -pl monitoring-core,monitoring-spring-boot-starter -am -DskipTests -q

# Copy demo source
COPY demo/demo-inventory-service/src demo/demo-inventory-service/src
COPY demo/demo-order-service/src demo/demo-order-service/src
COPY demo/demo-payment-service/src demo/demo-payment-service/src

ARG SERVICE_MODULE
RUN mvn clean package -f demo/pom.xml -pl ${SERVICE_MODULE} -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

ARG SERVICE_MODULE
COPY --from=builder /build/demo/${SERVICE_MODULE}/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
